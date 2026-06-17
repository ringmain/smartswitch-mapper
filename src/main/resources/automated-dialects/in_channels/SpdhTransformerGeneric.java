package cz.monetplus.smartswitch.spdh;

import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.util.Optional;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.neovisionaries.i18n.CountryCode;

import cz.monetplus.emv.*;
import cz.monetplus.emv.bertlv.Tag;
import cz.monetplus.smartswitch.common.model.*;
import cz.monetplus.smartswitch.common.model.enums.*;
import cz.monetplus.smartswitch.common.model.hsm.CoreSecKey;
import cz.monetplus.smartswitch.common.model.industry.asorsfood.AsorsFoodIndustry;
import cz.monetplus.smartswitch.common.model.industry.fuel.FuelCustomerData;
import cz.monetplus.smartswitch.common.model.industry.fuel.FuelIndustry;
import cz.monetplus.smartswitch.common.model.industry.fuel.FuelProduct;
import cz.monetplus.smartswitch.common.model.industry.qr.QRIndustry;
import cz.monetplus.smartswitch.common.utils.CensorUtils;
import cz.monetplus.smartswitch.common.utils.Hex;
import cz.monetplus.smartswitch.model.AcquirerResponseMappingResult;
import cz.monetplus.smartswitch.schannel.model.OptionalPinKey;
import cz.monetplus.smartswitch.spdh.enums.SoftPosDataVersion;
import cz.monetplus.smartswitch.spdh.exception.SpdhLocalException;
import cz.monetplus.smartswitch.spdh.model.*;
import cz.monetplus.smartswitch.spdh.tags.BaseSpdhTag;
import cz.monetplus.smartswitch.spdh.util.MonetFuelCommodityParser;
import cz.monetplus.spdh.commons.SpdhMessage;
import cz.monetplus.spdh.commons.enums.SpdhMessageSubType;
import cz.monetplus.spdh.commons.enums.SpdhResponseCode;
import io.vavr.Tuple;
import io.vavr.collection.List;
import io.vavr.control.Option;

public class SpdhTransformerGeneric implements SpdhTransformer {

    private static final Logger log = LoggerFactory.getLogger(SpdhTransformerGeneric.class);
    public static final String DEFAULT_RESPONSE_LANGUAGE_EN = "en";

    private final ResponseMessageConverter respMessageConv;

    public SpdhTransformerGeneric(ResponseMessageConverter respMessageConv) {
        super();
        this.respMessageConv = respMessageConv;
    }

    @Override
    public CoreTransaction toCore(SpdhRequestContext rc, SpdhMessage req, SpdhOperation oper) {
        final ProcType trnProcType = oper.getTrnProcType()
                .map(procType -> convertProcType(req, procType))
                .orElseThrow(() -> new SpdhLocalException(SpdhResponseCode.DeclinedInvalidTransaction, "Transaction type not found"));

        final EmvSmartCardScheme emvSmartCardScheme = detectEmvSmartCardScheme(rc,req,oper);
        final EMVChain emvChain = convertEmv(rc,req,oper);

        final Cardholder cardHolder = Cardholder.fromBuilder()
                .withPanSeqNumber(getCardSequenceNumber(emvChain).orElse(rc.getSpdhCardholderContext().getPanSeqNum()))
                .withTrack1(req.getFid(BaseSpdhTag.Track1).orElse(null))
                .withTrack2(req.getFid(BaseSpdhTag.Track2).orElse(rc.getDecryptedTrack2().orElse(null)))
                .withPan(convertPan(req))
                .withAltAccountNumber(req.getFid(BaseSpdhTag.AltAccountNumber).orElse(null))
                .withSrcChannelProtection(rc.getSrcChannelProtection())
                .build();

        final DccProcIndicator dccProcIndicator = req.getFid(BaseSpdhTag.PreAuthOperationType)
                .map(this::convertDccProcIndicator)
                .orElse(DccProcIndicator.UNKNOWN);

        final Amount amountDefault = spdhToAmount(req, rc);
        final Optional<Amount> amountFromDcc = dccOfferToAmount(req, rc);

        final Amounts amounts = Amounts
                .fromBaseAmounts(selectAmount(amountDefault, amountFromDcc, dccProcIndicator, rc), null, null)
                .withAdditAmount(convertCashBackReqAmount(oper, req))
                .withAdditAmount(convertTipReqAmount(req))
                .withAdditAmount(convertFoodReqAmount(rc,oper,req))
                .withAdditAmount(convertAmountReplacement(oper,req));

        final TrnDates trnDates = new TrnDates(
                rc.getTransmissionDttm(),
                rc.getTerminalDttm(),
                null, null, null,
                rc.getSrcChannelReqContext().getTransactionTimeoutDttm(),
                rc.getSrcChannelReqContext().isValidateTransactionTimeout());

        final Cad cad = convertCad(rc);
        final StrongCustomerAuthentication sca = convertSpdhReqToSCA(req);
        final PosInfo posInfo = convertPosInfo(rc, req, cad, emvChain, trnProcType, oper, sca);
        final Surcharge surcharge = convertSurcharge(req);

        final CoreTransaction base = CoreTransaction.builder(rc.getIds())
                .withCad(cad)
                .withAcquirer(rc.getAcquirer())
                .withTimeTrack(rc.getTimeTrack())
                .withProcessing(convertProcessing(rc, req, emvSmartCardScheme, emvChain, amounts, trnProcType, posInfo.getPosEntryMode()))
                .withTrnDates(trnDates)
                .withAmounts(amounts)
                .withCardholder(cardHolder)
                .withPosInfo(posInfo)
                .withProcResult(ProcResult.fromBuilder()
                        .withApprovalCode(req.getFid(BaseSpdhTag.ApprovalCode).map(String::trim).orElse(null))
                        .build())
                .withSpecialProcessing(convertSpecialProcessing(rc,req,oper, sca, amountDefault, amountFromDcc, dccProcIndicator, trnProcType, surcharge))
                .withIndustry(convertIndustry(rc, req, oper))
                .withProperties(convertProperties(req))
                .build()
                ;


        final CoreTransaction trnByType = finalizeByType(trnProcType, rc, base, req);
        final CoreTransaction trn = finalizeRequestBySpdhDialect(trnByType, trnProcType, rc, req);
        log.trace("{} - Created core transaction : {}",rc.getTrid(),trn);
        return trn;
    }

    protected Surcharge convertSurcharge(final SpdhMessage rc) {
        return Surcharge.none();
    }

    private boolean isDccProcIndReversed(DccProcIndicator dccProcIndicator) {
        return DccProcIndicator.REVERSED.equals(dccProcIndicator);
    }

    protected String convertPan(SpdhMessage req) {
        return req.getFid(BaseSpdhTag.Pan)
                .orElse(null);
    }

    /**
     * Tuhle metodu je potreba pretizit pro specike upravy pro jednotlive SDPH dialekty
     *
     * @param in V zakladu transformovana zprava na core
     * @param type typ operace
     * @param rc kontext ve kterem zprava vstoupila na server
     * @param spdh vstupni SPDH zprava
     * @return
     */
    protected CoreTransaction finalizeRequestBySpdhDialect(final CoreTransaction in, final ProcType type, final SpdhRequestContext rc,
                                                           final SpdhMessage spdh) {
        return in;
    }

    protected Cad convertCad(SpdhRequestContext rc) {
        return rc.getChildCad()
                .map(child -> child.builder()
                        .withCadProcData(child.getCadProcData().builder()
                                .withMotherCad(rc.getCadID())
                                .withCoreBatchId(rc.getCad().getCadProcData().getCoreBatchId().orElse(null))
                                .build())
                        .build())
                .orElseGet(rc::getCad);
    }

    protected CoreTransaction finalizeByType(final ProcType type, final SpdhRequestContext rc,
                                             final CoreTransaction base, final SpdhMessage spdh) {

        switch (type) {
            case VOICE:
            case BALANCE_INQUIRY:
            case CASH_ADVANCE:
            case CASH_BACK:
            case PRE_AUTH:
            case PRE_AUTH_INCREMENT:
            case PRE_AUTH_DECREMENT:
            case PURCHASE_ONLINE:
            case CARD_VERIFICATION:
            case TOKEN_NOT_REGISTERED:
                return finalizeOnline(base, rc, spdh);
            case REFUND:
                return finalizeRefund(base, rc, spdh);
            case PRE_AUTH_COMPLETION:
            case PURCHASE_OFFLINE:
            case REVERZAL_CUSTOMER:
            case REVERZAL_TECH:
            case INSTAL_CONF:
                return finalizeOffline(base, spdh);
            default:
                return base;
        }
    }

    protected CoreTransaction finalizeRefund(final CoreTransaction base, final SpdhRequestContext rc, final SpdhMessage spdh) {
        final Optional<SpdhResponseCode> spdhRespCode = SpdhResponseCode.fromCode(spdh.getResponseCode());
        final CoreResponseCode coreRespCode = spdhRespCode
                .map(this::mapResponseCodeToCore)
                .orElseGet(() -> mapRespCodeByGroup(spdhRespCode));
        final String cardHoldersPan = base.getCardholder().computePan().orElse(null);
        return base
                .withPinData(spdh
                        .getFid(BaseSpdhTag.PinBlock)
                        .<PinData>map(pbhex -> convertPinBlock(rc.getTrid(), cardHoldersPan, spdh, pbhex, rc.getOptionalPinKey(), rc)))
                .withApprovalCode(spdh.getFid(BaseSpdhTag.ApprovalCode).map(String::trim))
                .withResponseCode(coreRespCode);
    }

    protected CoreTransaction finalizeOnline(CoreTransaction base, SpdhRequestContext rc, final SpdhMessage spdh) {
        final String cardHoldersPan = base.getCardholder().computePan().orElse(null);
        final Optional<PinData> pinDataOpt = spdh
                .getFid(BaseSpdhTag.PinBlock)
                .<PinData>map(pbhex -> convertPinBlock(rc.getTrid(), cardHoldersPan, spdh, pbhex, rc.getOptionalPinKey(), rc));
        return base.withPinData(pinDataOpt);
    }

    protected CoreTransaction finalizeOffline(final CoreTransaction base,final SpdhMessage spdh) {
        Optional<SpdhResponseCode> spdhRespCode = SpdhResponseCode.fromCode(spdh.getResponseCode());
        CoreResponseCode coreRespCode = spdhRespCode
                .map(this::mapResponseCodeToCore)
                .orElseGet(() -> mapRespCodeByGroup(spdhRespCode) )
                ;

        return base
                .withApprovalCode(spdh.getFid(BaseSpdhTag.ApprovalCode).map(String::trim))
                .withResponseCode(coreRespCode)
                ;
    }

    @Override
    public CoreTransaction fillFromOrig(SpdhRequestContext rc, SpdhMessage req, CoreTransaction base, OriginalTransaction otrx) {
        switch (base.getProcType()) {
            case REFUND:
                return fillFromOrigRefund(req, base, otrx);
            case PRE_AUTH_COMPLETION:
                return fillFromOrigPreAuth(base, otrx);
            case REVERZAL_CUSTOMER:
            case REVERZAL_TECH:
            default:
                return fillFromOrigReversal(req, base, otrx);
        }
    }

    protected CoreTransaction fillFromOrigReversal(SpdhMessage req, CoreTransaction base, OriginalTransaction originalTransaction) {
        final boolean cardHolderOrExpiryNotPresent = cardHolderOrExpiryNotPresent(base);
        return base
                .withCardholder(getOrigCardHolder(base, originalTransaction, cardHolderOrExpiryNotPresent))
                .withPosInfo(getPosInfo(base, cardHolderOrExpiryNotPresent))
                .withEmvReq(getEmvChain(base, cardHolderOrExpiryNotPresent))
                .withOriginal(originalTransaction)
                .withApprovalCode(getOrigApprovalCode(base, originalTransaction))
                .transformProcessing(processing -> fillProcessingFromOrig(processing, originalTransaction))
                .withAmounts(getOrigAmounts(req, base, originalTransaction, false));
    }

    private Processing fillProcessingFromOrig(Processing processing, OriginalTransaction originalTransaction) {
        return processing
                .withPreAuth(getOrigPreauth(originalTransaction))
                .transformTrnIds(trnIds -> trnIds
                        .withAcqTrxId(convertAcqTrxId(trnIds, originalTransaction)));
    }

    private String convertAcqTrxId(TrnIds trnIds, OriginalTransaction originalTransaction) {
        Optional<String> originalAcqTrxIdOptional = originalTransaction.getProcessing().getTrnIds().getAcqTrxId();
        return originalAcqTrxIdOptional.orElseGet(() -> trnIds.getAcqTrxId().orElse(null));
    }

    private CoreTransaction fillFromOrigRefund(SpdhMessage req, CoreTransaction base, OriginalTransaction originalTransaction) {
        final boolean cardHolderOrExpiryNotPresent = cardHolderOrExpiryNotPresent(base);
        return base
                .withCardholder(getOrigCardHolder(base, originalTransaction, cardHolderOrExpiryNotPresent))
                .withPosInfo(getPosInfo(base, cardHolderOrExpiryNotPresent))
                .withEmvReq(getEmvChain(base, cardHolderOrExpiryNotPresent))
                .withOriginal(originalTransaction)
                .withApprovalCode(getOrigApprovalCode(base, originalTransaction))
                .transformProcessing(processing -> processing.withPreAuth(getOrigPreauth(originalTransaction)))
                .withAmounts(getOrigAmounts(req, base, originalTransaction, true));
    }

    private CoreTransaction fillFromOrigPreAuth(CoreTransaction base, OriginalTransaction originalTransaction) {
        final boolean cardHolderOrExpiryNotPresent = cardHolderOrExpiryNotPresent(base);
        return base
                .withCardholder(getOrigCardHolder(base, originalTransaction, cardHolderOrExpiryNotPresent))
                .withPosInfo(getPosInfo(base, cardHolderOrExpiryNotPresent))
                .withEmvReq(getEmvChain(base, cardHolderOrExpiryNotPresent))
                .transformProcessing(processing -> processing.withPreAuth(getOrigPreauth(originalTransaction)))
                .withOriginal(originalTransaction);
    }

    private Cardholder getOrigCardHolder(CoreTransaction base, OriginalTransaction originalTransaction, boolean cardHolderOrExpiryNotPresent) {
        if (cardHolderOrExpiryNotPresent) {
            final Optional<String> track2 = computeShortTrack2(originalTransaction);
            log.info("{} - Track2 for CardHolder is computing from OriginalTransaction. Track2 value is {}", base.getTrid(), track2.map(CensorUtils::censorShortTrack2).orElse("EMPTY"));
            return base.getCardholder()
                    .withTrack2(track2);
        }
        log.info("{} - CardHolder was used from CoreTransaction. CardHolder - {}", base.getTrid(), base.getCardholder());
        return base.getCardholder();
    }

    private PosInfo getPosInfo(CoreTransaction base, boolean cardHolderOrExpiryNotPresent) {
        final PosInfo posInfo = base.getPosInfo();
        if (cardHolderOrExpiryNotPresent) {
            return posInfo.withPosEntryMode(
                    new PosEntryMode(PanEntryMode.Manually, posInfo.getPosEntryMode().getPinEntryMode())
            );
        }
        return posInfo;
    }

    private EMVChain getEmvChain(CoreTransaction base, boolean cardHolderOrExpiryNotPresent) {
        if (cardHolderOrExpiryNotPresent) {
            return EMVChain.empty();
        }
        return base.getProcessing().getEmvReq();
    }

    private String getOrigApprovalCode(CoreTransaction base, OriginalTransaction originalTransaction) {
        return base.getProcResult()
                .getApprovalCode().orElseGet(() -> originalTransaction.getProcResult().getApprovalCode().orElse(null));
    }

    private Amounts getOrigAmounts(SpdhMessage req, CoreTransaction base, OriginalTransaction originalTransaction, boolean convertRefundAmounts) {
        Amounts amounts = base.getAmounts();
        final Optional<Amount> amountToReplace = isPrimaryAmountPresentInReversalRequest(req) ?
                Optional.empty() : Optional.ofNullable(originalTransaction.getAmounts().getPrimary());

        final Optional<CurrencyCode> currencyToReplace = isPrimaryCurrencyPresentInReversalRequest(req) ?
                Optional.empty() : Optional.ofNullable(originalTransaction.getAmounts().getPrimaryCurrency());

        if (amountToReplace.isPresent()) {
            amounts = amounts.withPrimary(amountToReplace.get());
        }

        if (currencyToReplace.isPresent()) {
            final Amount amount = amounts.getPrimary().withCurrency(currencyToReplace.get());
            amounts = amounts.withPrimary(amount);
        }

        if (convertRefundAmounts) {
            amounts = convertRefundAmounts(amounts, originalTransaction);
        }
        return amounts;
    }

    private PreAuth getOrigPreauth(OriginalTransaction originalTransaction) {
        return originalTransaction.getProcessing().getPreAuth()
                .orElse(null);
    }

    private Optional<String> computeShortTrack2(OriginalTransaction originalTransaction) {
        final Optional<String> computedPan = originalTransaction.getCardholder().computePan();
        final Optional<String> computedDateExpire = originalTransaction.getCardholder().computeDateExpire();
        if (computedPan.isPresent() && computedDateExpire.isPresent()) {
            return Optional.of(String.format(";%s=%s?", computedPan.get(), computedDateExpire.get()));
        }

        if (!computedPan.isPresent()) {
            log.warn("Pan was not computed from CardHolder data.");
        }

        if (!computedDateExpire.isPresent()) {
            log.warn("DateExpire was not computed from CardHolder data.");
        }

        return Optional.empty();
    }

    private boolean cardHolderOrExpiryNotPresent(CoreTransaction coreTransaction) {
        final boolean track2Present = coreTransaction
                .getCardholder()
                .computeTrack2().isPresent();
        final boolean expiryPresent = coreTransaction
                .getCardholder()
                .computeDateExpire().isPresent();

        final boolean result = !track2Present || !expiryPresent;
        log.debug("{} - Track2 was{} computed. DateExpire was{} computed", coreTransaction.getTrid(), track2Present ? "" : " not", expiryPresent ? "" : " not");

        return result;
    }

    private Amounts convertRefundAmounts(Amounts amounts, OriginalTransaction otrx) {
        if (CurrencyCode.XXX.equals(amounts.getPrimaryCurrency())) {
            CurrencyCode origCurr = otrx.getAmounts().getPrimaryCurrency();
            return Amounts.fromBaseAmounts(
                    amounts.getPrimary().withCurrency(origCurr),
                    amounts.getAmountBill().map(amount -> amount.withCurrency(origCurr)).orElse(null),
                    amounts.getBalance().map(amount -> amount.withCurrency(origCurr)).orElse(null)
            );
        } else if (!otrx.getAmounts().getPrimaryCurrency().equals(amounts.getPrimaryCurrency())) {
            boolean refundInOriginalCurrency = otrx.getSpecialProcessing()
                    .getDcc()
                    .map(origDcc -> origDcc.getDccRequest()		// Request terminal currency is same as in refund primary currency is swap happened
                            .getTermCurrency()
                            .map(origCurrency -> origCurrency.equals(amounts.getPrimaryCurrency()))
                            .orElse(false) &&
                            origDcc.getDccOffer()
                                    .getAmount()
                                    .equals(amounts.getPrimary().getValue()))
                    .orElse(false);

            if (refundInOriginalCurrency) {
                return amounts;
            }

            throw new SpdhLocalException(SpdhResponseCode.DeclinedInvalidTransaction,"Different currency between orig trn and refunded trn.");
        } else {
            return amounts;
        }
    }

    protected boolean isPrimaryAmountPresentInReversalRequest(SpdhMessage req) {
        return req.isPresent(BaseSpdhTag.Amount1);
    }

    private boolean isPrimaryCurrencyPresentInReversalRequest(SpdhMessage req) {
        return req.isPresent(BaseSpdhTag.CurrencyCode);
    }

    protected CoreResponseCode mapResponseCodeToCore(SpdhResponseCode spdhRespCode) {
        final SpdhResponseMapper respMapper = mapResponseCode(spdhRespCode)
                .orElseGet(() -> mapRespCodeByGroup(spdhRespCode));

        return CoreResponseCode
                .fromCode(respMapper.getCoreCode())
                .orElseGet(() -> {
                    if (respMapper.getCategory().equals(ResponseCodeMappingCategoryEnum.APPROVE))
                        return CoreResponseCode.APPROVED;
                    else
                        return CoreResponseCode.ERROR;
                });

    }

    protected Optional<SpdhResponseMapper> mapResponseCode(SpdhResponseCode spdhRespCode) {
        return SpdhResponseCodeMapper.findFromSpdh(spdhRespCode)
                .map(resp -> resp);
    }

    protected SpdhResponseCodeMapper mapRespCodeByGroup(SpdhResponseCode spdhRespCode) {

        final int src = Optional.ofNullable(spdhRespCode).map(SpdhResponseCode::getId).orElse(100);
        if (src < 11) {
            return SpdhResponseCodeMapper.R00;
        } else if (src > 10 && src < 50) {
            return SpdhResponseCodeMapper.R06;
        } else if (src > 49 && src < 100) {
            return SpdhResponseCodeMapper.R05;
        } else if (src > 99 && src < 150) {
            return SpdhResponseCodeMapper.R06;
        } else {
            return SpdhResponseCodeMapper.R06;
        }
    }

    protected CoreResponseCode mapRespCodeByGroup(Optional<SpdhResponseCode> spdhRespCodeOpt) {

        final int src = spdhRespCodeOpt.map(SpdhResponseCode::getId).orElse(100);
        if (src < 11) {
            return CoreResponseCode.APPROVED;
        } else if (src > 10 && src < 50) {
            return CoreResponseCode.ERROR;
        } else if (src > 49 && src < 100) {
            return CoreResponseCode.DO_NOT_HONOUR;
        } else if (src > 99 && src < 150) {
            return CoreResponseCode.ERROR;
        } else {
            return CoreResponseCode.ERROR;
        }
    }

    protected Optional<AdditionalAmount> convertTipReqAmount(SpdhMessage req) {
        return req
                .getFid(BaseSpdhTag.amountTip)
                .filter(amount -> !amount.equals("0"))
                .flatMap(amount -> req.getFid(BaseSpdhTag.CurrencyCode)
                        .map(cur -> Tuple.of(amount, cur))
                )
                .map(tupl -> Amount.fromStrings(tupl._1, tupl._2))
                .map(AdditionalAmount::tip)
                ;
    }

    protected Optional<AdditionalAmount> convertCashBackReqAmount(SpdhOperation oper, SpdhMessage req) {
        if (SpdhOperation.Cachback.equals(oper)) {
            return req
                    .getFid(BaseSpdhTag.Amount2)
                    .flatMap(amount -> req.getFid(BaseSpdhTag.CurrencyCode)
                            .map(cur -> Tuple.of(amount, cur))
                    )
                    .map(tupl -> Amount.fromStrings(tupl._1, tupl._2))
                    .map(AdditionalAmount::cashback)
                    ;
        } else {
            return Optional.empty();
        }
    }

    protected Optional<AdditionalAmount> convertFoodReqAmount(SpdhRequestContext rc, SpdhOperation oper, SpdhMessage req) {
        return req
                .getFid(BaseSpdhTag.AsorsFoodData)
                .flatMap(fidG -> convertFoodReqAmountInner(fidG, rc, oper, req))
                .map(AdditionalAmount::food)
                ;
    }

    protected Optional<AdditionalAmount> convertAmountReplacement(SpdhOperation oper, SpdhMessage req) {
        if (SpdhOperation.PurchaseAdjustment.equals(oper)) {
            return req
                    .getFid(BaseSpdhTag.Amount2)
                    .flatMap(amount -> req.getFid(BaseSpdhTag.CurrencyCode)
                            .map(cur -> Tuple.of(amount, cur))
                    )
                    .map(tupl -> Amount.fromStrings(tupl._1, tupl._2))
                    .map(AdditionalAmount::replacement)
                    ;
        } else {
            return Optional.empty();
        }
    }

    protected Optional<Amount> convertFoodReqAmountInner(String fidG, SpdhRequestContext rc, SpdhOperation oper, SpdhMessage req) {
        final String[] parts = StringUtils.split(StringUtils.trimToEmpty(fidG), ",");
        if (parts == null) {
            log.info("{} - incorrect format of ASROS FOOD amount - data are null - [{}]",rc.getTrid(),fidG);
            return Optional.empty();
        }
        if (parts.length != 3) {
            log.info("{} - incorrect format of ASROS FOOD amount- FID 9G is not from 3 items - [{}]",rc.getTrid(),fidG);
            return Optional.empty();
        }
        return Optional.of(Amount.fromStrings(parts[1], req.getFid(BaseSpdhTag.CurrencyCode).orElse("999")));
    }

    protected Amount selectAmount(Amount amountDefault, Optional<Amount> amountFromDcc, DccProcIndicator dccProcIndicator, SpdhRequestContext rc) {
        if (isDccProcIndReversed(dccProcIndicator) && amountFromDcc.isPresent()) {
            log.info("{} - use AmountFromDcc `{}` due to DccProcIndicator: {}", rc.getTrid(), amountFromDcc.get(), dccProcIndicator);
            return amountFromDcc.get();
        }
        return amountDefault;
    }

    protected Amount spdhToAmount(final SpdhMessage spdh, SpdhRequestContext rc) {
        return extractPrimaryAmount(spdh);
    }

    protected Optional<Amount> dccOfferToAmount(final SpdhMessage spdh, SpdhRequestContext rc) {
        return spdh.getFid(BaseSpdhTag.DccOffer)
                .map(dccOffer -> Amount.fromStrings(
                        extractValueFromDcc(dccOffer, rc),
                        extractCurrencyNumberFromDcc(dccOffer, rc)));
    }

    private String extractValueFromDcc(String spdhFid9O, SpdhRequestContext rc) {
        final String[] split = spdhFid9O.split(",");
        if (split.length > 2) {
            return separateValue(split[2]);
        }
        log.info("{} - incorrect length of spdh Fid9O - FID 9O does not contain value - [{}]", rc.getTrid(), spdhFid9O);
        return "0";
    }

    private String extractCurrencyNumberFromDcc(String spdhFid9O, SpdhRequestContext rc) {
        final String[] split = spdhFid9O.split(",");
        if (split.length > 1) {
            String currencyCode = split[1];

            if (CurrencyCode.fromCurrencyCode(currencyCode).isPresent()) {
                return currencyCode;
            }
        }
        log.info("{} - incorrect length of spdh Fid9O - FID 9O does not contain currency - [{}]", rc.getTrid(), spdhFid9O);
        return "XXX";
    }

    private Amount extractPrimaryAmount(SpdhMessage spdh) {
        if (spdh.isPresent(BaseSpdhTag.Amount1) || spdh.isPresent(BaseSpdhTag.CurrencyCode)) {
            return Amount.fromStrings(
                    spdh.getFid(BaseSpdhTag.Amount1).orElse("0"),
                    spdh.getFid(BaseSpdhTag.CurrencyCode).orElse("999"));
        } else {
            return Amount.base();
        }
    }

    private Amount convertSplitPaymentAmount(SpdhMessage spdh) {
        try {
            if (spdh.isPresent(BaseSpdhTag.AmountSplit)) {
                long mainAmount = Long.parseLong(spdh.getFid(BaseSpdhTag.Amount1).orElse("0"));
                long splitAmount = Long.parseLong(spdh.getFid(BaseSpdhTag.AmountSplit).orElse("0"));
                return new Amount((mainAmount - splitAmount),
                        CurrencyCode.fromCurrencyNumber(spdh.getFid(BaseSpdhTag.CurrencyCode).orElse("999")).orElse(CurrencyCode.XXX));
            }
        } catch (NumberFormatException e) {
            log.debug("Amount is in wrong format.", e);
        } catch (Exception e) {
            log.error("Exception occured by parsing amounts from spdh.", e);
        }
        return Amount.base();
    }

    protected SpecialProcessing convertSpecialProcessing(SpdhRequestContext rc, SpdhMessage req, SpdhOperation oper, StrongCustomerAuthentication sca, Amount amountDefault, Optional<Amount> amountFromDcc, DccProcIndicator dccProcIndicator, ProcType procType, Surcharge surcharge) {
        return SpecialProcessing.fromBuilder()
                .withEcommerce(convertSpdhReqToEcom(req,oper,rc))
                .withInstallment(req
                        .getFid(BaseSpdhTag.Installment)
                        .map(instFid -> convertSpdhReqToInstallment(instFid, req, oper))
                        .orElseGet(InstallmentProc::none))
                .withSCA(sca)
                .withDCC(convertDcc(rc, req, oper, amountDefault, amountFromDcc, dccProcIndicator).orElse(null))
                .withSplitPayment(convertSplitPayment(req, rc, procType).orElse(SplitPayment.none()))
                .withSoftPos(convertSoftPos(rc, req))
                .withSurcharge(surcharge)
                .build();
    }

    protected Optional<SplitPayment> convertSplitPayment(SpdhMessage req, SpdhRequestContext rc, ProcType procType) {
        if (ProcType.SPLIT_PAYMENT.equals(procType)) {
            int splitAmount = req.getFid(BaseSpdhTag.AmountSplit)
                    .map(s -> {
                        try {
                            return Integer.parseInt(s);
                        } catch (NumberFormatException e) {
                            return 0;
                        }
                    }).orElse(0);

            return Optional.of(SplitPayment.fromBuilder()
                    .withSplitPaymentItems(
                            List.of(
                                            SplitPaymentItem.fromBuilder()
                                                    .withSplitAmount(Math.toIntExact(convertSplitPaymentAmount(req).getValue()))
                                                    .withItemNo(1)
                                                    .build(),
                                            SplitPaymentItem.fromBuilder()
                                                    .withSplitAmount(splitAmount)
                                                    .withItemNo(2)
                                                    .build())
                                    .toJavaList())
                    .build());
        }
        return Optional.empty();
    }

    protected SoftPos convertSoftPos(SpdhRequestContext rc, SpdhMessage req) {
        return req.getFid(BaseSpdhTag.SoftPosData)
                .map(data -> convertSoftPosFrom8s(rc, data))
                .orElse(null);
    }

    private SoftPos convertSoftPosFrom8s(SpdhRequestContext rc, String softPosData) {
        final String fidVersion = StringUtils.substring(softPosData, 0, 2);
        final SoftPosDataVersion version = SoftPosDataVersion.fromCode(fidVersion);

        switch (version) {
            case V_01:	return convertSoftPosVersion01(softPosData);
            case V_02:	return convertSoftPosVersion02(softPosData);
            default:
                log.info("{} - Unsupported fidVersion '{}' for field 8s. Cannot convert SoftPosData!", rc.getTrid(), fidVersion);
                return null;
        }
    }

    private SoftPos convertSoftPosVersion01(String softPosData) {
        final String sdkId = StringUtils.defaultIfEmpty(StringUtils.substring(softPosData, 2, 18).trim(), null);
        final String customerId = StringUtils.defaultIfEmpty(StringUtils.substring(softPosData, 18, 48).trim(), null);
        final String posVersion = StringUtils.defaultIfEmpty(StringUtils.substring(softPosData, 48, 80).trim(), null);
        final String deviceOsVersion = StringUtils.defaultIfEmpty(StringUtils.substring(softPosData, 80, 112).trim(), null);
        final String deviceModel = StringUtils.defaultIfEmpty(StringUtils.substring(softPosData, 112, 144).trim(), null);
        final String deviceIpAddress = StringUtils.defaultIfEmpty(StringUtils.substring(softPosData, 144, 190).trim(), null);

        return new SoftPos(null, posVersion, deviceOsVersion, deviceModel, deviceIpAddress, sdkId, customerId);
    }

    private SoftPos convertSoftPosVersion02(String softPosData) {
        final String sdkId = StringUtils.defaultIfEmpty(StringUtils.substring(softPosData, 2, 66).trim(), null);
        final String customerId = StringUtils.defaultIfEmpty(StringUtils.substring(softPosData, 66, 96).trim(), null);
        final String posVersion = StringUtils.defaultIfEmpty(StringUtils.substring(softPosData, 96, 128).trim(), null);
        final String deviceOsVersion = StringUtils.defaultIfEmpty(StringUtils.substring(softPosData, 128, 160).trim(), null);
        final String deviceModel = StringUtils.defaultIfEmpty(StringUtils.substring(softPosData, 160, 192).trim(), null);
        final String deviceIpAddress = StringUtils.defaultIfEmpty(StringUtils.substring(softPosData, 192, 238).trim(), null);

        return new SoftPos(null, posVersion, deviceOsVersion, deviceModel, deviceIpAddress, sdkId, customerId);
    }

    protected Optional<Dcc> convertDcc(SpdhRequestContext rc, SpdhMessage spdh, SpdhOperation oper, Amount amountDefault, Optional<Amount> amountFromDcc, DccProcIndicator dccProcIndicator) {

        if (SpdhOperation.DccSale.equals(oper)
                || SpdhOperation.DccRefund.equals(oper)
                || SpdhOperation.DccPreauth.equals(oper)
                || SpdhOperation.DccMoto.equals(oper)) {
            log.info("{} - This is Dcc request Transaction", rc.getTrid());

            return initDccRequest(oper)
                    .withDccRequest(spdh.getFid(BaseSpdhTag.DccRequest)
                            .map(this::toDCCRequest)
                            .orElse(null))
                    .withDccIdentifikator
                            (spdh.getFid(BaseSpdhTag.DccIdentifier)
                                    .map(this::convertDccIdentifier)
                                    .orElse(null))
                    .withCustomerResult(spdh.getFid(BaseSpdhTag.DccResult)
                            .map(this::convertCustomerResult)
                            .orElse(null))
                    .build()
                    .toOptional();

        } else if (SpdhOperation.NormalPurchase.equals(oper) || SpdhOperation.Moto.equals(oper)) {
            //purchase nebo moto s dcc result a dcc id
            if (spdh.getFid(BaseSpdhTag.DccIdentifier).isPresent()) {
                //purchase s dcc result a dcc id
                return DccBuilder.purchase()
                        .withDccIdentifikator(spdh.getFid(BaseSpdhTag.DccIdentifier)
                                .map(this::convertDccIdentifier)
                                .orElse(null))
                        .withCustomerResult(spdh.getFid(BaseSpdhTag.DccResult)
                                .map(this::convertCustomerResult)
                                .orElse(null))
                        .withDccProcIndicator(spdh.getFid(BaseSpdhTag.PreAuthOperationType)
                                .map(this::convertDccProcIndicator)
                                .orElse(null))
                        .withDccOffer(spdh.getFid(BaseSpdhTag.DccOffer)
                                .map(dccOfferReq -> convertDccOffer(rc.getTrid(), dccProcIndicator, amountDefault, dccOfferReq))
                                .orElse(null))
                        .build()
                        .toOptional();
            }

            return Dcc.none().toOptional();

        } else if (SpdhOperation.MerchandiseReturnOnline.equals(oper)
                || SpdhOperation.MerchandiseReturnForcePost.equals(oper)) {

            // refund s dcc result a dcc id
            if (spdh.getFid(BaseSpdhTag.DccIdentifier).isPresent()) {
                return DccBuilder.refund()
                        .withDccIdentifikator(spdh.getFid(BaseSpdhTag.DccIdentifier)
                                .map(this::convertDccIdentifier)
                                .orElse(null))
                        .withCustomerResult(spdh.getFid(BaseSpdhTag.DccResult)
                                .map(this::convertCustomerResult)
                                .orElse(null))
                        .withDccProcIndicator(spdh.getFid(BaseSpdhTag.PreAuthOperationType)
                                .map(this::convertDccProcIndicator)
                                .orElse(null))
                        .withDccOffer(spdh.getFid(BaseSpdhTag.DccOffer)
                                .map(dccOfferReq -> convertDccOffer(rc.getTrid(), dccProcIndicator, amountDefault, dccOfferReq))
                                .orElse(null))
                        .build()
                        .toOptional();
            } else if (spdh.getFid(BaseSpdhTag.DccExtension).isPresent()) {
                // refund s kurzem
                // to   smsw fid [9F]
                // from smsw fid [9O] + [9R]

                return Dcc.refund_ex_rate()
                        .builder()
                        .withDccOffer(spdh.getFid(BaseSpdhTag.DccExtension)
                                .map(dccRefund -> convertDccRefundToOffer(dccRefund, amountDefault))
                                .orElseGet(DccOffer::none))
                        .withCustomerResult(true)
                        .withDccRequest(DccRequest.fromBuilder() // nastavuje se pro ulozeni trxDcc do DB
                                .withTermCurrency(amountDefault.getCurrency())
                                .withTermCountry(CountryCode.getByCode(amountDefault.getCurrency().getCurrencyNumber()))
                                .build())
                        .withDccProcIndicator(spdh.getFid(BaseSpdhTag.PreAuthOperationType)
                                .map(this::convertDccProcIndicator)
                                .orElse(null))
                        .build()
                        .toOptional();
            }

            return Dcc.none().toOptional();

        } else if (SpdhOperation.PreauthorizationPurchase.equals(oper)) {
            // 9I + 9R - odpoved na nabidku
            if (spdh.getFid(BaseSpdhTag.DccIdentifier).isPresent()) {
                return DccBuilder.preauth()
                        .withDccIdentifikator(spdh.getFid(BaseSpdhTag.DccIdentifier)
                                .map(this::convertDccIdentifier)
                                .orElse(null))
                        .withCustomerResult(spdh.getFid(BaseSpdhTag.DccResult)
                                .map(this::convertCustomerResult)
                                .orElse(null))
                        .withDccProcIndicator(spdh.getFid(BaseSpdhTag.PreAuthOperationType)
                                .map(this::convertDccProcIndicator)
                                .orElse(null))
                        .withDccOffer(spdh.getFid(BaseSpdhTag.DccOffer)
                                .map(dccOfferReq -> convertDccOffer(rc.getTrid(), dccProcIndicator, amountDefault, dccOfferReq))
                                .orElse(null))
                        .build()
                        .toOptional();
            }
            return Dcc.none().toOptional();
        } else {
            return Dcc.none().toOptional();
        }

    }

    private DccOffer convertDccOffer(final String trid, final DccProcIndicator dccProcIndicator, final Amount amountIn, final String dccOfferReq) {
        if (isDccProcIndReversed(dccProcIndicator)) {
            final DccOffer dccOffer = deserializeDcc(dccOfferReq);
            if (dccOffer == null) {
                log.info("{} - cannot switch dccOffer (fid 9O) with Amount, fid 9O is invalid {}", trid, dccOfferReq);
                return null;
            }
            final DccOffer dccOfferSwitched = dccOffer.builder()
                    .withCurrency(amountIn.getCurrency())
                    .withCardholderExponent((long) amountIn.getCurrency().getDigitsAfterDecimalPoint())
                    .withTrnAmountCardholder(amountIn.getValue())
                    .build();

            final String serialize = serializeDcc(dccOfferSwitched);
            log.info("{} - DccOffer request was switched from [{}] to [{}]", trid, dccOfferReq, serialize);
            return dccOfferSwitched;
        }
        return null;
    }

    private boolean convertCustomerResult(String customerResCode) {
        return DccResult.CUSTOMER_ALLOWED.getCode().equals(customerResCode);
    }

    protected DccOffer convertDccRefundToOffer(String dccRefund, Amount amount) {
        final String[] split = dccRefund.split(",");
        if (split.length == 3) {

            final Optional<CurrencyCode> terminalCurrencyCode = CurrencyCode.fromCurrencyNumber(split[2]);

            return DccOffer.fromBuilder()
                    .withEffectiveExchangeRateExponent(parseNullableLongSafe(split[1], 0, 1))
                    .withEffectiveExchangeRate(parseNullableLongSafe(split[1], 1, split[1].length()))
                    .withAmount(amount.getValue())
                    .withCurrency(terminalCurrencyCode
                            .orElse(null)) // card currency
                    .withAmountExponent((long)amount.getCurrency().getDigitsAfterDecimalPoint()) // terminal currency
                    .withCardholderExponent(terminalCurrencyCode
                            .map(CurrencyCode::getDigitsAfterDecimalPoint)
                            .map(Integer::longValue)
                            .orElse(0L))
                    .build();
        }
        return null;
    }

    protected Dcc convertDccPreauth(String dccExt, SpdhMessage spdh) {
        final String preAuthType = spdh.getFid(BaseSpdhTag.PreAuthOperationType).orElse("NO");

        switch (preAuthType) {
            case "I":
                return Dcc.preauth_inc();

            case "D":
                return Dcc.preauth_dec();

            default:
                return Dcc.preauth();
        }
    }

    protected Long convertDccIdentifier(String dccId) {
        if (isHexa(dccId))
            return Long.valueOf(dccId, 16);

        return null;
    }

    private boolean isHexa(String hex) {
        if (hex.matches("-?[0-9a-fA-F]+")) {
            return true;
        }

        log.error("Invalid hex data from Dcc identifier data: {}", hex);
        throw new SpdhLocalException(SpdhResponseCode.TransactionFormatError, "Invalid hex data from Dcc identifier "+hex);
    }

    private DccBuilder initDccRequest(SpdhOperation oper) {

        switch (oper) {
            case DccSale:
            case DccMoto:
                return DccBuilder.offer();
            case DccRefund:
                return DccBuilder.dcc_refund();
            case DccPreauth:
                return DccBuilder.dcc_preauth();
            case MerchandiseReturnForcePost:
            case MerchandiseReturnOnline:
                return DccBuilder.refund();
            case NormalPurchase:
                return DccBuilder.purchase();
            case PreauthorizationPurchase:
                return DccBuilder.preauth();

            default:
                return DccBuilder.none();
        }
    }

    protected DccRequest toDCCRequest(String dccRequest) {
        if (dccRequest.length() >= 13) {
            return DccRequest.fromBuilder()
                    .withDeviceType(DccDeviceType.fromCode(dccRequest.substring(0, 1)).orElse(null))
                    .withTermCurrency(dccConvertCurrencyNum(dccRequest.substring(1, 4)).orElse(null))
                    .withTermCountry(convertCountryCode(dccRequest.substring(4, 7)))
                    .withCardCurrency(dccConvertCurrencyNum(dccRequest.substring(7, 10)).orElse(null))
                    .withCardCountry(convertCountryCode(dccRequest.substring(10, 13)))
                    .build();
        }
        return null;
    }

    private Optional<CurrencyCode> dccConvertCurrencyNum(String num) {
        if (num == null)
            return Optional.empty();
        if (num.equals("000"))
            return Optional.empty();
        return CurrencyCode.fromCurrencyNumber(num);
    }

    private CountryCode convertCountryCode(String codeNumber) {
        try {
            return CountryCode.getByCode(Integer.parseInt(codeNumber));
        } catch (NumberFormatException e) {
            log.info("Cannot convert country number {}", codeNumber);
            return null;
        }
    }

    protected Industry convertIndustry(SpdhRequestContext rc, SpdhMessage req, SpdhOperation oper) {
        return Industry
                .fromBuilder()
                .withAsorsFood(getAsorsFood(rc, req, oper))
                .withQR(getQr(rc,req))
                .transformFuelIndustry(fuel -> convertFuelIndustry(fuel, rc, req))
                .build();
    }

    protected FuelIndustry convertFuelIndustry(FuelIndustry fuelIndustry, SpdhRequestContext rc, SpdhMessage req) {
        return convertFuelProducts(rc, req)
                .map(fuelIndustry::withProducts)
                .map(fuel -> fuel.withCustomerData(convertFuelCustomerData(req)))
                .orElse(fuelIndustry.withCustomerData(convertFuelCustomerData(req)));
    }

    private FuelCustomerData convertFuelCustomerData(SpdhMessage spdh) {
        return new FuelCustomerData(
                spdh.getFid(BaseSpdhTag.VehicleCode).orElse(null),
                spdh.getFid(BaseSpdhTag.DriverNo).orElse(null),
                spdh.getFid(BaseSpdhTag.VehicleTachometer).orElse(null),
                null,
                spdh.getFid(BaseSpdhTag.UnencryptedId).orElse(null),
                spdh.getFid(BaseSpdhTag.OrderId).orElse(null),
                spdh.getFid(BaseSpdhTag.VehicleTag).orElse(null));
    }

    protected Optional<List<FuelProduct>> convertFuelProducts(SpdhRequestContext rc, SpdhMessage req) {
        final String protocolVersion = req.getFid(BaseSpdhTag.ProjectVersion).orElse("00");
        if ("40".equals(protocolVersion) && req.isPresent(BaseSpdhTag.ProductData)) {
            return Optional.of(MonetFuelCommodityParser.deserializeIfsfV40Style(rc.getTrid(), req));
        } else if ("30".equals(protocolVersion) && req.isPresent(BaseSpdhTag.ProductData)) {
            return Optional.of(MonetFuelCommodityParser.deserializeIfsfStyle(rc.getTrid(), req));
        } else if (req.isPresent(BaseSpdhTag.CommodityHeader)) {
            if (!rc.getFuelProducts().isEmpty()) {
                log.info("{} - Cache fuel products Added To Core Transaction[{}]",rc.getTrid(), rc.getFuelProducts());
                return Optional.of(rc.getFuelProducts());
            } else {
                log.info("{} - An indicator for cached FUEL product data is present, but no FUEL data was extracted from the cache!",rc.getTrid());
                return Optional.empty();
            }
        } else if (req.isPresent(BaseSpdhTag.CommodityData)) {
            return Optional.of(MonetFuelCommodityParser.deserializeMytoStyle(req));
        } else {
            return Optional.empty();
        }
    }


    protected AsorsFoodIndustry getAsorsFood(SpdhRequestContext rc, SpdhMessage req, SpdhOperation oper) {
        if (req.isPresent(BaseSpdhTag.AsorsFoodData)) {
            return req.getFid(BaseSpdhTag.AsorsFoodData)
                    .map(fid9G -> convertAsorsFoodIndustry(fid9G, rc, req, oper))
                    .orElse(null);
        } else
            return null;
    }

    protected AsorsFoodIndustry convertAsorsFoodIndustry(String fid9G, SpdhRequestContext rc, SpdhMessage req, SpdhOperation oper) {
        final String[] parts = StringUtils.split(StringUtils.trimToEmpty(fid9G), ",");
        if (parts == null) {
            log.info("{} - incorrect format of ASROS FOOD - data are null - [{}]",rc.getTrid(),fid9G);
            return null;
        }
        if (parts.length != 3) {
            log.info("{} - incorrect format of ASROS FOOD - FID 9G is not from 3 items - [{}]",rc.getTrid(),fid9G);
            return null;
        }
        if ("0".equals(parts[0])) {
            return AsorsFoodIndustry.baseFull();
        } else if ("1".equals(parts[0])) {
            return AsorsFoodIndustry.basePartial();
        } else {
            log.info("{} - incorrect format of ASROS FOOD - unknown auth mode - [{}]",rc.getTrid(),fid9G);
            return null;
        }

    }

    protected QRIndustry getQr(SpdhRequestContext rc, SpdhMessage req) {
        return null;
    }

    protected StrongCustomerAuthentication convertSpdhReqToSCA(final SpdhMessage spdh) {
        return spdh.getFid(BaseSpdhTag.AdditionalPointOfServiceData)
                .map(this::toSCA)
                .orElseGet(StrongCustomerAuthentication::none);

    }

    private StrongCustomerAuthentication toSCA(String f60Data) {
        if(StringUtils.trimToNull(f60Data) == null || (f60Data.length() < 7 || f60Data.length() > 10)) {
            log.info("SCA (FID6 SFID0 - Strong customer authentication) flag is empty or do not contain 10 characters, flag {}", f60Data);
            return StrongCustomerAuthentication.none();
        }
        final boolean scaSupported = "2".equals(f60Data.substring(5, 6));
        final boolean secondTransaction = "3".equals(f60Data.substring(6, 7));

        return new StrongCustomerAuthentication(scaSupported,secondTransaction);
    }

    protected Ecommerce convertSpdhReqToEcom(SpdhMessage spdh, SpdhOperation spdhOper, SpdhRequestContext rc) {
        if (SpdhOperation.Moto.equals(spdhOper)) {
            return Ecommerce.fromBuilder()
                    .withCvc(spdh.getFid(BaseSpdhTag.ManualCvd).map(String::trim).orElse(null))
                    .withEciCode("01")
                    .build();
        } else if (spdh.isPresent(BaseSpdhTag.ManualCvd)) {
            return Ecommerce.fromBuilder()
                    .withCvc(spdh.getFid(BaseSpdhTag.ManualCvd).map(String::trim).orElse(null))
                    .withEciCode("01")
                    .build();
        } else {
            return Ecommerce
                    .base()
                    .withCvc(rc.getSpdhCardholderContext().getCvv())
                    ;
        }
    }

    protected InstallmentProc convertSpdhReqToInstallment(String installmentFid, SpdhMessage spdh, SpdhOperation oper) {
        if (StringUtils.trimToNull(installmentFid) == null) {
            return InstallmentProc.none();
        }
        else if (SpdhOperation.InstallmentConfirmation.equals(oper) && installmentFid.length() >= 81 && installmentFid.startsWith("MCI")) {
            InstallmentProcBuilder ipBuilder = InstallmentProc.fromBuilder()
                    .withInstallmentIndicator(InstallmentIndicator.CONFIRMED)
                    .withType(InstallmentType.fromCode(installmentFid.substring(3, 5)))
                    .withPaymentOption(InstallmentPaymentOption.fromCode(installmentFid.substring(5, 6)));

            String version = installmentFid.substring(6, 7);

            if(version.equals("0")) {
                // format 1 (list)
                ipBuilder.withInstMcFormat1(convertInstallmentMcFormat1(installmentFid.substring(7, 71)));
            } else {
                // format 2
                ipBuilder.withInstMcFormat2(convertInstallmentMcFormat2(installmentFid.substring(7, 75)));
            }

            return ipBuilder.build();
        } else if ("MCI".equalsIgnoreCase(installmentFid)) {
            return InstallmentProc.possible();
        } else if (installmentFid.startsWith("MCI")) {
            return InstallmentProc.possible();
        } else {
            return InstallmentProc.none();
        }
    }

    protected Integer parseNullableIntegerSafe(String full, int from, int to) {
        try {
            return Integer.parseInt(full.substring(from,to));
        } catch (Exception e) {
            return null;
        }
    }

    protected Long parseNullableLongSafe(String full, int from, int to) {
        try {
            return Long.parseLong(StringUtils.trim(full.substring(from,to)));
        } catch (Exception e) {
            return null;
        }
    }

    protected String parseInstallmentReceiptDataSafe(String trid, String full, int lenFrom, int lenTo) {
        try {
            final int len = Optional
                    .ofNullable(parseNullableIntegerSafe(full, lenFrom, lenTo))
                    .orElse(0);
            if (len == 0) {
                return null;
            } else if (lenTo+len > full.length()){
                log.warn("{} - Installment data length is longer than content of fid.",trid);
                return null;
            } else {
                return full.substring(lenTo, lenTo+len);
            }
        } catch (Exception e) {
            return null;
        }
    }

    public InstallmentMcFormat2 convertInstallmentMcFormat2(String format2) {
        if(format2 == null) {
            return null;
        }

        InstallmentMcFormat2Builder format2Build = InstallmentMcFormat2Builder.none();

        format2Build.withInterestRate(parseNullableIntegerSafe(format2, 4, 9)); //5
        format2Build.withInstFee(parseNullableLongSafe(format2, 9, 21)); //12
        format2Build.withAnnualPercRate(parseNullableIntegerSafe(format2, 21, 26)); //5
        format2Build.withTotalAmountDue(parseNullableLongSafe(format2, 26, 38)); //12
        //38-50, 50-62, 62-64
        format2Build.withNumOfInst(parseNullableIntegerSafe(format2, 62, 64)); //2
        format2Build.withMinNumOfInst(parseNullableIntegerSafe(format2, 64, 66)); //2
        format2Build.withMaxNumOfInst(parseNullableIntegerSafe(format2, 66, 68)); //2

        return format2Build.build();
    }

    public List<InstallmentMcFormat1> convertInstallmentMcFormat1(String format1) {
        if(format1 == null) {
            return null;
        }

        InstallmentMcFormat1Builder format1Build = InstallmentMcFormat1Builder.none();

        format1Build.withInterestRate(parseNullableIntegerSafe(format1, 4, 9));//5
        format1Build.withInstFee(parseNullableLongSafe(format1, 9, 21)); //12
        format1Build.withAnnualPercRate(parseNullableIntegerSafe(format1, 21, 26)); //5
        format1Build.withTotalAmountDue(parseNullableLongSafe(format1, 26, 38)); //12
        format1Build.withFirstInstAmount(parseNullableLongSafe(format1, 38, 50)); //12
        format1Build.withSubseqInstAmount(parseNullableLongSafe(format1, 50, 62)); //12
        format1Build.withNumOfInst(parseNullableIntegerSafe(format1, 62, 64)); //2

        return List.of(format1Build.build());
    }

    protected EmvSmartCardScheme detectEmvSmartCardScheme(SpdhRequestContext rc, SpdhMessage req, SpdhOperation oper) {
        return req.getFid(BaseSpdhTag.EmvRequestData)
                .flatMap(fid -> parseHexString(fid, 1, 2))
                .map(EmvSmartCardScheme::fromCode)
                .orElse(EmvSmartCardScheme.None)
                ;
    }

    protected EMVChain convertEmv(SpdhRequestContext rc, SpdhMessage req, SpdhOperation oper) {
        return EMVChain
                .empty()
                .withEmvChain(req.getFid(BaseSpdhTag.EmvRequestData).map(this::convertFid6O))
                .withEmvChain(req.getFid(BaseSpdhTag.EmvRequestDataAdditional).map(this::convertFid6P))
                .withEmvChain(req.getFid(BaseSpdhTag.EmvSupplRequestCless).map(fid -> this.convertFid6q(fid, rc)))
                .withEmvChain(req.getFid(BaseSpdhTag.EmvRequestAdditional2).map(fid -> this.convertFid8q(fid, rc)))
                .withEmvChain(convertEMVAmountOtherForCashBack(rc, req, oper))
                ;
    }

    protected EMVChain convertFid8q(String fid, SpdhRequestContext rc) {
        return parseHexString(fid, 3, 4096)
                .flatMap(sBuff -> unhexEmvValue(sBuff,true))
                .map(EMVChain::fromBytes)
                .map(emvChain -> this.removeInvalidTags(emvChain, rc))
                .orElseGet(EMVChain::new);
    }

    protected EMVChain convertFid6O(String fid) {
        final String smartCardScheme = parseHexString(fid, 1, 2).orElse("00");
        final EMVChain base = EMVChain
                .empty()
                .withEmvValue(parseEmvFromString(fid, EMVTags.CRYPTOGRAM_INFORMATION_DATA, 3, 2))
                .withEmvValue(parseEmvFromString(fid, EMVTags.TERMINAL_COUNTRY_CODE, 5, 3))
                .withEmvValue(parseEmvFromString(fid, EMVTags.TRANSACTION_DATE, 8, 6))
                .withEmvValue(parseEmvFromString(fid, EMVTags.APP_CRYPTOGRAM, 14, 16))
                .withEmvValue(parseEmvFromString(fid, EMVTags.APPLICATION_INTERCHANGE_PROFILE, 30, 4))
                .withEmvValue(parseEmvFromString(fid, EMVTags.APP_TRANSACTION_COUNTER, 34, 4))
                .withEmvValue(parseEmvFromString(fid, EMVTags.UNPREDICTABLE_NUMBER, 38, 8))
                .withEmvValue(parseEmvFromString(fid, EMVTags.TERMINAL_VERIFICATION_RESULTS, 46, 10))
                .withEmvValue(parseEmvFromString(fid, EMVTags.TRANSACTION_TYPE, 56, 2))
                ;
        if (smartCardScheme.equals("01"))
            return base
                    .withEmvValue(parseEmvFromString(fid, EMVTags.TRANSACTION_CURRENCY_CODE, 58, 3))
                    .withEmvValue(parseEmvFromString(fid, EMVTags.AMOUNT_AUTHORISED_NUMERIC, 61, 12))
                    .withEmvValue(parseEmvFromString(fid, EMVTags.ISSUER_APPLICATION_DATA, 73, 64))
                    ;
        else if (smartCardScheme.equals("00"))
            return base
                    .withEmvValue(parseEmvFromString(fid, EMVTags.ISSUER_APPLICATION_DATA, 58, 64))
                    ;
        else
            return base;
    }

    protected EMVChain convertFid6P(String fid) {
        final String smartCardScheme = parseHexString(fid, 1, 2).orElse("00");
        final EMVChain base = EMVChain
                .empty()
                .withEmvValue(parseEmvFromString(fid, EMVTags.PAN_SEQUENCE_NUMBER, 3, 2))
                .withEmvValue(parseEmvFromString(fid, EMVTags.TERMINAL_TYPE, 5, 2))
                ;

        if (smartCardScheme.equals("00")) {
            return base;
        } else {
            final EMVChain baseV2 = base
                    .withEmvValue(parseEmvFromString(fid, EMVTags.CVM_RESULTS, 7, 6))
                    .withEmvValue(parseEmvFromString(fid, EMVTags.APP_VERSION_NUMBER_TERMINAL, 13, 4))
                    ;
            if (smartCardScheme.equals("01"))
                return baseV2
                        .withEmvValue(parseEmvFromString(fid, EMVTags.DEDICATED_FILE_NAME, 17, 32))
                        ;
            else if (smartCardScheme.equals("02"))
                return baseV2
                        .withEmvValue(parseEmvFromString(fid, EMVTags.TERMINAL_CAPABILITIES, 17, 6))
                        .withEmvValue(parseEmvFromString(fid, EMVTags.DEDICATED_FILE_NAME, 23, 32))
                        ;
            else
                return baseV2;
        }
    }

    protected Processing convertProcessing(
            SpdhRequestContext rc,
            SpdhMessage req,
            EmvSmartCardScheme emvSmartCardScheme,
            EMVChain emvChain,
            Amounts amounts,
            ProcType procType,
            PosEntryMode posEntryMode) {

        return Processing
                .fromBuilder()
                .withProcType(procType)
                .withEmvReq(emvChain)
                .withEmvSmartCardScheme(emvSmartCardScheme)
                .withSrcProcessingBatch(convertBatchInfo(rc.getSpdhCutoverContext()))
                .withSettlementCtrx(SettlementCtrxBuilder
                        .base()
                        .withAddToSettlement(rc.getSpdhCutoverContext().addTosettlement())
                        .build())
                .withPreAuth(getPreAuth(procType, rc.getTrid(), amounts, posEntryMode.getPanEntryMode()))
                .withTrnIds(TrnIds.fromBuilder()
                        .withVs(convertVs(rc, req).orElse(null))
                        .withVs2(convertVs2(rc, req).orElse(null))
                        .withReceipt(convertReceipt(rc,req).orElse(null))
                        .withAcqTrxId(rc
                                .getChildCadSpdh()
                                .orElseGet(rc::getCadSpdh)
                                .getSpdhSeqNumber().toResponse())
                        .withRid(req.getFid(BaseSpdhTag.Rid).orElse(null))
                        .build())
                .withProcessingIds(ProcessingIds.fromBuilder()
                        .withSourceChannelName(rc.getInChannelName())
                        .withSrcProtoFamily(SrcProtoFamily.SPDH)
                        .withOfflineAuthentication(req.getFid(BaseSpdhTag.OfflineAuthentication).orElse(null))
                        .build())
                .withIndicators(Indicators.fromBuilder()
                        .withDraftCaptureFlag(DraftCaptureFlag
                                .fromCode(req.getFid(BaseSpdhTag.DraftCaptureFlag).orElse("1"))
                                .orElse(DraftCaptureFlag.authorizeAndCapture))
                        .withPartialAuthIndicator(PartialAuthIndicator
                                .fromSpdh(req.getFid(BaseSpdhTag.PartialAmountInd)))
                        .withProcessorTokenRequest(req.getFid(BaseSpdhTag.ProcessorToken)
                                .map("1"::equals)
                                .orElse(false))
                        .withMessageReason(convertMessageReason(req))
                        .build())
                .withTags(convertTags(rc))
                .build();
    }

    protected MessageReason convertMessageReason(SpdhMessage req) {
        return req.getFid(BaseSpdhTag.CustomerID).map(spdhCode -> {
            switch (spdhCode) {
                case "4000":	return MessageReason.CUST_CANCEL;
                case "4013":	return MessageReason.POS_DELIV_FAIL;
                case "4020":	return MessageReason.AUTH_ERR;
                case "4021":	return MessageReason.TIMEOUT;
                case "4351":	return MessageReason.SIGN_REJECT;
                case "4353":	return MessageReason.ERR_EMV_PROC;
                case "4363":	return MessageReason.SYSTEM_MALFUNCTION;
                default:		return convertMessageReasonByMessageSubtype(req);
            }
        }).orElse(convertMessageReasonByMessageSubtype(req));
    }

    private MessageReason convertMessageReasonByMessageSubtype(SpdhMessage req) {
        return SpdhMessageSubType.fromCode(req.getSpdhHead().getMessageSubType()).map(subtype -> {
            switch (subtype) {
                case CustomerCanceledReversal:		return MessageReason.CUST_CANCEL;
                case TimeoutReversalOnline:			return MessageReason.TIMEOUT;
                case TerminalOrControllerReversal:
                default:							return MessageReason.SYSTEM_MALFUNCTION;
            }
        }).orElse(MessageReason.SYSTEM_MALFUNCTION);
    }

    protected Tags convertTags(SpdhRequestContext rc) {
        Tags tags = Tags.base();

        if (rc.isFullOffline()) {
            tags = tags.withTag(TagKey.FULL_OFFLINE.getKey());
        }

        return tags;
    }

    protected BatchInfo convertBatchInfo(SpdhCutoverContext scc){
        return BatchInfo.cutovers(scc.getCutoverCode(), scc.getActiveDate());
    }

    protected Optional<String> convertVs(SpdhRequestContext rc, SpdhMessage req) {
        return Option
                .ofOptional(req.getFid(BaseSpdhTag.VariableSymbol))
                .orElse(() -> Option.ofOptional(req.getFid(BaseSpdhTag.OriginalReceiptCode)))
                .toJavaOptional();
    }

    protected Optional<String> convertVs2(SpdhRequestContext rc, SpdhMessage req) {
        return req.getFid(BaseSpdhTag.InvoiceNumberOriginal);
    }

    protected Optional<String> convertReceipt(SpdhRequestContext rc, SpdhMessage req) {
        if (req.getFid(BaseSpdhTag.DccIdentifier).isPresent() && req.getFid(BaseSpdhTag.DccResult).isPresent()) {
            return Optional.empty();
        }
        return req
                .getFid(BaseSpdhTag.ReceiptCode);
    }

    protected PosInfo convertPosInfo(SpdhRequestContext reqContext, SpdhMessage spdh, Cad cad, EMVChain emvChain, ProcType procType, SpdhOperation spdhOper, StrongCustomerAuthentication sca) {
        final PosEntryMode posEntryMode = spdh
                .getFid(BaseSpdhTag.POSEntryMode)
                .map(PosEntryMode::fromSpdh)
                .orElseGet(PosEntryMode::unknown);

        final Optional<String> posInfo6x = spdh.getFid(BaseSpdhTag.PosInfoCsob).flatMap(pi6x -> validate6xPosInfo(pi6x, reqContext));

        final String posConditionCode = convertPosConditionCode(spdh,spdhOper);
        final String responseTextLanguage = getResponseMessageLocale(
                spdh.getFid(BaseSpdhTag.LanguageCode)
                        .flatMap(langCode -> validateLanguageFromSpdhRequest(reqContext.getTrid(), langCode)),
                cad);
        final CardholderPresentIndicator cardholderPresentIndicator = convertCardholderPresentIndicator(
                posConditionCode,spdh,spdhOper);
        final CardPresentIndicator cardPresentIndicator = convertCardPresentIndicator(posConditionCode, spdh, spdhOper);
        final TransactionStatusIndicator transactionStatusIndicator = convertTxnStatInd(procType);
        final TransactionSecurityIndicator transactionSecurityIndicator = TransactionSecurityIndicator.noSecurityConcern;
        final CardholderActivatedTerminalIndicator cardholderActivatedTerminal = CardholderActivatedTerminalIndicator.notCAT;

        final Optional<Integer> cvmCodeOpt = getCvmCode(emvChain);
        final CardholderIdMethod cardholderIdMethod = convertCardholderIdMethod(spdh, cvmCodeOpt, spdhOper, sca, cad);
        final CardholderAuthEntity cardholderAuthEntity = convertCardholderAuthEntity(spdh,cvmCodeOpt);

        PosInfo posInfo = PosInfo.fromBuilder()
                .withPosEntryMode(posEntryMode)
                .withPosConditionCode(posConditionCode)
                .withResponseTextLanguage(responseTextLanguage)
                .withCardHolderPresentIndicator(cardholderPresentIndicator)
                .withCardPresentIndicator(cardPresentIndicator)
                .withTransactionStatusIndicator(transactionStatusIndicator)
                .withTransactionSecurityIndicator(transactionSecurityIndicator)
                .withCardholderActivatedTerminalIndicator(cardholderActivatedTerminal)
                .withCardholderIdMethod(cardholderIdMethod)
                .withCardholderAuthEntity(cardholderAuthEntity)
                .withPosControlSetup(convertPosControlSetup(spdh.getEmployeeId()))
                .withPosUserId(convertPosUserId(spdh.getEmployeeId()))
                .withTermType(convertTermType(spdh))
                .withTermHw(convertTermHw(spdh))
                .build();

        return posInfo6x.isPresent() ? enrichPosInfoRequest(posInfo, posInfo6x.get()) : posInfo;
    }

    protected Optional<String> validate6xPosInfo(String posInfo6x, SpdhRequestContext rc) {
        if (posInfo6x == null || posInfo6x.length() != 6) {
            log.warn(rc.toMarker(), "{} - Invalid PoS info! FID 6X content: '{}'", rc.getTrid(), posInfo6x);
            return Optional.empty();
        } else if (posInfo6x.contains("000000")) {
            log.info(rc.toMarker(), "{} - Invalid PoS info! FID 6X content: '{}'", rc.getTrid(), posInfo6x);
            return Optional.empty();
        }
        return Optional.of(posInfo6x);
    }

    protected PosInfo enrichPosInfoRequest(PosInfo posInfoIn, String posInfo6x) {
        return posInfoIn
                .withCardHolderPresentIndicator(
                        CardholderPresentIndicator.fromCode(posInfo6x.substring(0, 1))
                                .orElse(posInfoIn.getCardHolderPresentIndicator()))
                .withCardPresentIndicator(
                        CardPresentIndicator.fromCode(posInfo6x.substring(1, 2))
                                .orElse(posInfoIn.getCardPresentIndicator()))
                .withTransactionStatusIndicator(
                        TransactionStatusIndicator.fromCode(posInfo6x.substring(2, 3))
                                .orElse(posInfoIn.getTransactionStatusIndicator()))
                .withTransactionSecurityIndicator(
                        TransactionSecurityIndicator.fromCode(posInfo6x.substring(3, 4))
                                .orElse(posInfoIn.getTransactionSecurityIndicator()))
                .withCardholderActivatedTerminalIndicator(
                        CardholderActivatedTerminalIndicator.fromCode(posInfo6x.substring(4, 5))
                                .orElse(posInfoIn.getCardholderActivatedTerminalIndicator()))
                .withCardholderIdMethod(
                        CardholderIdMethod.fromCode(posInfo6x.substring(5, 6))
                                .orElse(posInfoIn.getCardholderIdMethod()));
    }

    protected TermType convertTermType(SpdhMessage spdh) {
        return spdh.getFid(BaseSpdhTag.SoftPosData).isPresent() ? TermType.SOFTPOS : TermType.GENERAL;
    }

    protected Optional<String> validateLanguageFromSpdhRequest(String trid, String spdhReqLangIn) {
        final String spdhReqLang = StringUtils.trimToNull(spdhReqLangIn);
        if (isLanguageFromSpdhRequestValid(spdhReqLang)) {
            return Optional.ofNullable(spdhReqLang);
        } else {
            log.info("{} - Invalid request language. Language value=[{}]",trid,spdhReqLangIn);
            return Optional.empty();
        }
    }

    protected boolean isLanguageFromSpdhRequestValid(String spdhReqLang) {
        return !(
                spdhReqLang == null
                        || spdhReqLang.length() != 2
                        || !StringUtils.isAlphanumeric(spdhReqLang));
    }

    protected CardholderPresentIndicator convertCardholderPresentIndicator(String posConditionCode, SpdhMessage spdh, SpdhOperation spdhOper) {
        if (SpdhOperation.Moto.equals(spdhOper) || "08".equals(posConditionCode)) {
            return CardholderPresentIndicator.notPresentMailOrFax;
        } else if ("01".equals(posConditionCode)) {
            return CardholderPresentIndicator.notPresentUnspecifiedReason;
        } else {
            return CardholderPresentIndicator.isPresent;
        }
    }

    protected CardPresentIndicator convertCardPresentIndicator(String posConditionCode, SpdhMessage spdh, SpdhOperation spdhOper) {
        if (SpdhOperation.Moto.equals(spdhOper)) {
            return CardPresentIndicator.isNotPresent;
        } else {
            switch (posConditionCode) {
                case "01":
                case "05":
                case "08":
                    return CardPresentIndicator.isNotPresent;
                default:
                    return CardPresentIndicator.isPresent;
            }
        }

    }

    protected String convertPosConditionCode(SpdhMessage spdh, SpdhOperation spdhOper) {
        if (SpdhOperation.Moto.equals(spdhOper)) {
            return "08";
        }
        return spdh.getFid(BaseSpdhTag.POSConditionCode).orElse("00");
    }

    protected String convertPosUserId(String employeeId) {
        if(StringUtils.trimToNull(employeeId) == null || employeeId.length() < 6) {
            return null;
        }

        return employeeId.substring(2, 6);
    }

    protected PosControlSetup convertPosControlSetup(String employeeId) {
        if(StringUtils.trimToNull(employeeId) == null || employeeId.length() < 6) {
            return PosControlSetup.UNKNOWN;
        }

        final String setupCode = employeeId.substring(1, 2);

        switch (setupCode) {
            case "0": return PosControlSetup.STANDALONE;
            case "1": return PosControlSetup.ECR;
            default: return PosControlSetup.UNKNOWN;
        }
    }

    protected String getResponseMessageLocale(final Optional<String> reqLangCode, final Cad cad) {
        try {
            return  reqLangCode
                    .orElseGet(() -> Optional.ofNullable(cad)
                            .flatMap(nncad -> nncad
                                    .getCadProcData()
                                    .getCountry())
                            .orElseGet(() -> DEFAULT_RESPONSE_LANGUAGE_EN));
        } catch (Exception e) {
            log.error("{} - Fail to determine response language!",e);
            return DEFAULT_RESPONSE_LANGUAGE_EN;
        }
    }

    protected TransactionStatusIndicator convertTxnStatInd(ProcType procType) {
        switch (procType) {
            case PRE_AUTH_COMPLETION: return TransactionStatusIndicator.preauthorizedRequest;
            case CASH_BACK: return TransactionStatusIndicator.cachBack;
            default: return TransactionStatusIndicator.normalRequest;
        }
    }

    protected CardholderAuthEntity convertCardholderAuthEntity(SpdhMessage spdh,Optional<Integer> cvmCodeOpt) {
        if (cvmCodeOpt.isPresent()) {
            switch (cvmCodeOpt.get()) {
                case 0: return CardholderAuthEntity.unknown;
                case 1: return CardholderAuthEntity.iccOfflinePin;
                case 2:return CardholderAuthEntity.onlinePin;
                case 3:return CardholderAuthEntity.iccOfflinePin;
                case 4:return CardholderAuthEntity.iccOfflinePin;
                case 5:return CardholderAuthEntity.iccOfflinePin;
                case 30:return CardholderAuthEntity.byMerchant;
                case 31:return CardholderAuthEntity.notAuthenticated;
                default:
                    return CardholderAuthEntity.unknown;
            }
        } else {
            if (spdh.getFid(BaseSpdhTag.PinBlock).isPresent())
                return CardholderAuthEntity.onlinePin;
            else
                return CardholderAuthEntity.byMerchant;
        }
    }

    protected CardholderIdMethod convertCardholderIdMethod(SpdhMessage spdh, Optional<Integer> cvmCodeOpt, SpdhOperation oper, StrongCustomerAuthentication sca, Cad cad) {
        if (SpdhOperation.Moto.equals(oper)) {
            return CardholderIdMethod.noneAndCarholderNotPresent;
        } else if (cvmCodeOpt.isPresent()) {

            // pri druhe navazujici sca transakci musi byt transakce stejna jak ta puvodni a proto je potreba kontrolovat kombinaci pinblocku a sca ke spravnemu nastaveni CardholderIdMethod.
            if (sca.isSubsequentTrx() && spdh.getFid(BaseSpdhTag.PinBlock).isPresent()) {
                return CardholderIdMethod.pin;
            }

            switch (cvmCodeOpt.get()) {
                case 0: return CardholderIdMethod.unknown;
                case 1:
                case 2:
                case 3:
                case 4:
                case 5:
                    return CardholderIdMethod.pin;
                case 30:return CardholderIdMethod.signature;
                case 31:return CardholderIdMethod.noneAndCardholderPresent;
                default:
                    return CardholderIdMethod.unknown;
            }
        } else {
            if (spdh.getFid(BaseSpdhTag.PinBlock).isPresent())
                return CardholderIdMethod.pin;
            else
            if(checkTermDataOutputCapability(cad.getTermCapability().getTermDataOuputCapability())){
                return CardholderIdMethod.noneAndCardholderPresent;
            }
            return CardholderIdMethod.signature;
        }
    }

    protected static boolean checkTermDataOutputCapability(TermDataOuputCapability tdoc) {
        switch (tdoc) {
            case displayOnly:
            case none:
                return true;
            default:
                return false;
        }
    }

    protected Optional<Integer> getCvmCode(EMVChain emvChain) {
        return emvChain
                .findTlv("9f34")
                .map(EMVValue::getValueBytes)
                .filter(bv -> bv != null && bv.length >= 1)
                .map(bv -> bv[0] & 0x3f)
                ;
    }

    protected Optional<String> getCardSequenceNumber(EMVChain req) {
        return req
                .findTlv("5f34")
                .map(EMVValue::getValueHexString)
                ;

    }

    protected EMVChain convertFid6q(final String fid, SpdhRequestContext rc) {
        return parseHexString(fid, 3, 4096)
                .flatMap(sBuff -> unhexEmvValue(sBuff,true))
                .map(EMVChain::fromBytes)
                .map(this::removeInvoluntaryTags)
                .map(emvChain -> this.removeInvalidTags(emvChain, rc))
                .orElseGet(EMVChain::new)
                ;
    }

    protected Optional<String> convertFid6Q(final EmvSmartCardScheme scheme,final SpdhResponseMapper respMapper, final EMVChain emv) {
        if (scheme.equals(EmvSmartCardScheme.None))
            return Optional.empty();

        final StringBuilder sb = new StringBuilder();
        sb.append(scheme.getCode());
        if (scheme.equals(EmvSmartCardScheme.EmvVersion2))
            sb.append(emv.findTlv("8A").map(EMVValue::getValueString).orElse(mapSpdhRespCodeToEmv(respMapper)));

        sb.append(emv.findTlv("91").map(EMVValue::getValueHexString).orElse(""));
        return Optional.of(sb.toString()).map(String::toUpperCase);
    }

    protected Optional<EMVChain> convertEMVAmountOtherForCashBack(SpdhRequestContext rc, SpdhMessage req, SpdhOperation oper){
        if (SpdhOperation.Cachback.equals(oper) && shouldSetCashbackAmountFromAmount2(req, rc)) {
            return req
                    .getFid(BaseSpdhTag.Amount2)
                    .map(this::formatEMVAmountOtherForCashBack);
        } else {
            return Optional.empty();
        }
    }

    protected boolean shouldSetCashbackAmountFromAmount2(SpdhMessage message, SpdhRequestContext rc) {
        final Optional<String> emvRequestData = message.getFid(BaseSpdhTag.EmvRequestData);
        final Optional<EMVValue> emvCashBackData = message.getFid(BaseSpdhTag.EmvSupplRequestCless)
                .map(fid -> this.convertFid6q(fid, rc))
                .flatMap(emvChain -> emvChain.findTlv("9F03"));

        return !emvCashBackData.isPresent() && emvRequestData.isPresent();
    }

    protected EMVChain formatEMVAmountOtherForCashBack(String amntOtherRawSPDH) {
        return EMVChain.fromHexString("9F0306"+StringUtils.leftPad(amntOtherRawSPDH, 12, '0'));
    }

    protected Optional<String> convertSzepCloseLoop(CoreTransaction ctrx) {
        return ctrx.getProperties().get(PropertyId.SZEP_CLOSE_LOOP_RESPONSE)
                .map(property -> Optional.ofNullable(property.getText()))
                .orElse(Optional.empty());
    }

    protected PinData convertPinBlock(String trid, String cardHoldersPan, SpdhMessage spdh, String hexPinBlock, OptionalPinKey optPinKey, SpdhRequestContext rc) {

        final PinblockFormat pinblockFormat = convertReqPinblockFormat(spdh, cardHoldersPan);

        try {
            final byte[] pinblock = unHexSafe(hexPinBlock,"Fail to unhex pinblock!");
            return optPinKey
                    .toEither()
                    .fold(
                            errMsg -> convertPinBlockOnPinKeyNotFound(trid, errMsg, pinblockFormat, pinblock),
                            pinKey -> convertPinBlockOnPinKeyFound(pinKey, pinblockFormat, pinblock, rc));
        } catch (SpdhLocalException e) {
            throw e;
        } catch (Exception e) {
            log.error("Conversion of pinblock failed! {}",e.getMessage(),e);
            throw new SpdhLocalException(SpdhResponseCode.TransactionFormatError, "Invalid format of PINblock");
        }
    }

    protected PinData convertPinBlockOnPinKeyFound(CoreSecKey pinKey, PinblockFormat pinblockFormat, byte[] pinblock, SpdhRequestContext rc) {
        return new PinData(pinblock, new PinContext(pinKey, pinblockFormat));
    }

    protected PinData convertPinBlockOnPinKeyNotFound(String trid, String notFoundReason, PinblockFormat pinblockFormat, byte[] pinblock) {
        log.info("{} - Unable to obtain PIN source key. Reason : {}",trid,notFoundReason);
        return new PinData(pinblock, new PinContext(CoreSecKey.none(), pinblockFormat));
    }

    protected PinblockFormat convertReqPinblockFormat(SpdhMessage spdhMessage, String cardHoldersPan) {
        Boolean isAes = spdhMessage.getFid(BaseSpdhTag.KSN_ADD_INFO).map(fid -> "AES".equals(fid)).orElse(false);
        return spdhMessage.getFid(BaseSpdhTag.PinblockFormat)
                .map(StringUtils::trimToNull)
                .map(messageData -> {
                    switch (messageData) {
                        case "0":
                            return cardHoldersPan != null ? PinblockFormat.iso9564_0(cardHoldersPan) : PinblockFormat.unknown();
                        case "1":
                            return PinblockFormat.iso9564_1();
                        case "4":
                            return cardHoldersPan != null ? PinblockFormat.iso9564_4(cardHoldersPan) : PinblockFormat.unknown();
                        case "AS24":
                            return convertAS24PinblockFormat(spdhMessage);
                        default:
                            return getDefaulReqPinblockFormat(cardHoldersPan, isAes);
                    }
                })
                .orElse(getDefaulReqPinblockFormat(cardHoldersPan, isAes));
    }

    protected PinblockFormat convertAS24PinblockFormat (SpdhMessage spdhMessage) {
        if (spdhMessage.getFid(BaseSpdhTag.Track2).isPresent()) {
            return spdhMessage.getFid(BaseSpdhTag.Track2)
                    .flatMap(Cardholder::parsePanFromTrack2)
                    .map(PinblockFormat::iso9564_0)
                    .orElseThrow(() -> new IllegalArgumentException("Track2 or parsed PAN is missing"));
        }
        return PinblockFormat.unknown();
    }

    protected PinblockFormat getDefaulReqPinblockFormat(String cardholdersPan, boolean isAes) {
        if (cardholdersPan == null) {
            return PinblockFormat.unknown();
        }
        if (isAes) {
            return PinblockFormat.iso9564_4(cardholdersPan);
        }
        return PinblockFormat.iso9564_0(cardholdersPan);
    }

    protected byte[] unHexSafe(String toUnhex, String failMessage) {
        try {
            return Hex.unhex(toUnhex);
        } catch (Exception e) {
            throw new SpdhLocalException(SpdhResponseCode.TransactionFormatError, failMessage+" '"+toUnhex+"'");
        }
    }

    protected String mapSpdhRespCodeToEmv(final SpdhResponseMapper respMapper) {
        final int spdhCode = respMapper.getSpdhCode();
        if (spdhCode < 50)
            return "00";
        else if (spdhCode > 99 && spdhCode < 150)
            return "01";
        else if (spdhCode > 899 && spdhCode < 950)
            return "04";
        else
            return "05";
    }

    protected Optional<String> convertFid6R(final EmvSmartCardScheme scheme, final EMVChain emv) {
        if (scheme.equals(EmvSmartCardScheme.None)) {
            return Optional.empty();
        } else if (!emv.contains("71") && !emv.contains("72")) {
            return Optional.empty();
        }

        final StringBuilder sb = new StringBuilder();
        sb.append(scheme.getCode());
        final EMVChain scriptChain = EMVChain
                .empty()
                .withEmvValue(emv.findTlv("71"))
                .withEmvValue(emv.findTlv("72"))
                ;

        sb.append(scriptChain.toHexaString());

        return Optional.of(sb.toString()).map(String::toUpperCase);
    }

    protected Optional<EMVValue> parseEmvFromString(final String source,final Tag emvTag,final int startAt,final int len) {
        return parseEmvFromString(source, emvTag, startAt, len, false);
    }

    protected Optional<EMVValue> parseEmvFromString(final String source,final Tag emvTag,final int startAt,final int len, final boolean bypassZeroCheck) {
        return parseHexString(source, startAt, len)
                .flatMap(str -> unhexEmvValue(str,bypassZeroCheck))
                .map(bval -> new EMVValue(emvTag, bval.length, EMVUtil.getLengthBytes(bval.length), bval))
                ;
    }

    protected Optional<byte[]> unhexEmvValue(final String hex, final boolean bypassZeroCheck) {
        if (hex == null || StringUtils.trimToNull(hex) == null)
            return Optional.empty();

        final String fixLenHex = (hex.length() % 2) != 0 ? "0"+hex : hex;

        try {
            return Optional.of(Hex.unhex(fixLenHex));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    protected Optional<String> parseHexString(final String source,final int startAtH,final int len) {
        final int startAt = startAtH -1;
        if (source.length() < startAt)
            return Optional.empty();

        final int reqEnd = startAt+len;
        final int end = (source.length() < reqEnd) ?  source.length() : reqEnd;

        final String raw = source.substring(startAt , end);
        if (StringUtils.trimToEmpty(raw) == null)
            return Optional.empty();

        return Optional.of(raw);
    }

    @Override
    public SpdhMessage fromCore(final SpdhRequestContext rc, final SpdhMessage req, final CoreTransaction coreTransaction) {
        final ProcResult procResult = coreTransaction.getProcResult();

        final SpdhResponseMapper respMapper = convertResponseCode(procResult.getResponseCode())
                .orElseGet(() -> SpdhResponseCodeMapper.R06);

        final String translatedResponseMessage = respMessageConv.respDisplay(
                rc.getNetworkId(),
                coreTransaction.getCad(),
                req.getFid(BaseSpdhTag.LanguageCode),
                respMapper);

        validateCoreAcquirerResponseMapping(coreTransaction,respMapper,translatedResponseMessage);

        SpdhMessage resSpdhBase = req.baseResponse(respMapper.getSpdhCodeString(), rc.getTransmissionDttmWithTimeZone())
                .withFid(BaseSpdhTag.ApprovalCode, convertResponseApprovalCode(coreTransaction))
                .withFid(BaseSpdhTag.Amount1, convertAmountForPartialResponse(procResult.getResponseCode(), coreTransaction))
                .withFid(BaseSpdhTag.CurrencyCode, convertCurrencyForPartialResponse(procResult.getResponseCode(), coreTransaction.getAmounts()))
                .withFid(BaseSpdhTag.TransportKey, convertTransportKey(rc))
                .withFid(BaseSpdhTag.ResponseDisplay, translatedResponseMessage)
                .withFid(BaseSpdhTag.SequenceNumber, rc.getCadSpdh().getSpdhSeqNumber().toResponse())
                .withFid(BaseSpdhTag.EmvResponseData,
                        convertFid6Q(coreTransaction.getProcessing().getEmvSmartCardScheme(), respMapper, procResult.getEmvRes()))
                .withFid(BaseSpdhTag.EmvAdditionalResponseData,
                        convertFid6R(coreTransaction.getProcessing().getEmvSmartCardScheme(), procResult.getEmvRes()))
                .withFid(BaseSpdhTag.Echo, req.getFid(BaseSpdhTag.Echo))
                .withFid(BaseSpdhTag.Installment, convertCoreResInstallmentToSpdh(coreTransaction.getSpecialProcessing().getInstallment()))
                .withFid(BaseSpdhTag.ErrorFlag, convertErrorFlagToSpdh(coreTransaction.getSpecialProcessing().getSca()))
                .withFid(BaseSpdhTag.AvailableBalance, convertAvailBalanceForPartialResponse(procResult.getResponseCode(), coreTransaction))
                .withFid(BaseSpdhTag.SzepCloseLoop, convertSzepCloseLoop(coreTransaction))
                .withFid(BaseSpdhTag.AsorsFoodData, convertResponseAsorsFood(coreTransaction))
                .withFid(BaseSpdhTag.DccIdentifier, convertResponseDccIdentifier(coreTransaction))
                .withFid(BaseSpdhTag.DccOffer, convertResponseDccOffer(coreTransaction, procResult.getResponseCode()))
                .withFid(BaseSpdhTag.DccResult, convertResponseDccResult(coreTransaction))
                .withFid(BaseSpdhTag.RefundId, convertRefundId(coreTransaction))
                .withFid(BaseSpdhTag.IssuerTrxId, coreTransaction.getProcResult().getIssuerTrxId())
                .withFid(BaseSpdhTag.OriginalPosReceiptCode, coreTransaction.getProcResult().getIssuerRespMessages().getAdvertisingText())
                .removeFid(BaseSpdhTag.ProcessorToken.getTag())
                .withFid(BaseSpdhTag.ProcessorToken, coreTransaction.getCardholder().getProcessorToken())
                .withFid(BaseSpdhTag.Par, coreTransaction.getSpecialProcessing().getDigitalWallet().getPar())
                .withFid(BaseSpdhTag.TraceId, coreTransaction.getProcResult().getTraceId())
                .withFid(BaseSpdhTag.MonetToken, getChannelToken(coreTransaction))
                .withFid(BaseSpdhTag.ResponseData, convertResponseData(coreTransaction))
                .withFid(BaseSpdhTag.ProcessorId, convertProcessorId(coreTransaction))
                .withFid(BaseSpdhTag.ResponseDataMap, convertResponseDataMap(coreTransaction))
                .withFid(BaseSpdhTag.IndustryData, convertAllowedProducts(coreTransaction))
                .withFid(BaseSpdhTag.CoreTRID, convertCoreTrid(coreTransaction.getTrid()))
                ;

        return finalizeResponseBySpdhDialect(rc, req, resSpdhBase, coreTransaction, respMapper, translatedResponseMessage);
    }

    protected Optional<String> convertTransportKey(SpdhRequestContext rc) {
        if (!rc.isDoNotSendTkInResponse()) {
            return rc.getCadSpdh().getKeyTransitTmk();
        } else {
            return Optional.empty();
        }
    }

    protected Optional<String> convertResponseData(CoreTransaction coreTransaction){
        return coreTransaction.getProcResult().getResponseDataMap()
                .map(ResponseDataMap::serialize);
    }

    protected Optional<String> convertResponseDataMap(CoreTransaction coreTransaction){
        return Optional.empty();
    }

    protected Optional<String> convertProcessorId(CoreTransaction coreTransaction){
        return coreTransaction.getProcResult().getProcessorId();
    }

    protected Optional<String> convertResponseApprovalCode(CoreTransaction coreTransaction) {
        return coreTransaction.getProcResult().getApprovalCode();
    }

    protected Optional<String> getChannelToken(CoreTransaction ctrx) {
        return Optional.empty();
    }

    protected String respDisplay(final SpdhRequestContext rc, final Cad cad, Optional<String> reqLangCode, CoreResponseCode crc) {

        final SpdhResponseMapper respMapper = convertResponseCode(crc)
                .orElseGet(() -> SpdhResponseCodeMapper.R06);

        return respMessageConv.respDisplay(
                rc.getNetworkId(),
                cad,
                reqLangCode,
                respMapper);
    }

    protected Optional<String> convertResponseDccOffer(CoreTransaction ctrx, CoreResponseCode responseCode) {
        if (CoreResponseCode.APPROVED.equals(responseCode) && isDccApplied(ctrx.getSpecialProcessing()))
            return ctrx.getSpecialProcessing().getDcc()
                    .map(Dcc::getDccOffer)
                    .map(this::serializeDcc);

        return Optional.empty();
    }

    private boolean isDccApplied(SpecialProcessing processing) {
        return processing.getDcc()
                .map(dcc ->
                        (!DccStatus.NONE.equals(dcc.getDccStatus()))
                                && (DccResult.DCC_APPLIED.equals(dcc.getDccResult()) || BooleanUtils.isTrue(dcc.isCustomerAccepted())))
                .orElse(false);
    }

    protected String serializeDcc(DccOffer dccOffer) {
        return new StringBuilder()
                .append(dccOffer.getCardType().getCode())
                .append(",")
                .append(dccOffer.getCurrency() != null ? dccOffer.getCurrency().getCurrencyCode() : "")
                .append(",")
                .append(converExponentAndValue(dccOffer.getCardholderExponent(), dccOffer.getTrnAmountCardholder()))
                .append(",")
                .append(converExponentAndValue(dccOffer.getEffectiveExchangeRateExponent(), dccOffer.getEffectiveExchangeRate()))
                .append(",")
                .append(dccOffer.getLanguage() != null ? dccOffer.getLanguage().toLowerCase() : "")
                .append(",")
                .append(convertEcbRate(dccOffer))
                .append(",")
                .append(dccOffer.getCurrency() != null ? dccOffer.getCurrency().getCurrencyNumber() : "")
                .toString();
    }

    private DccOffer deserializeDcc(String dccOfferStr) {

        final String[] split = dccOfferStr.split(",");
        if (split.length >= 3) {

            return DccOffer.fromBuilder()
                    .withCardType(DccType.fromCode(split[0]))
                    .withCurrency(CurrencyCode.fromCurrencyCode(split[1]).orElse(null))
                    .withCardholderExponent(Long.valueOf(separateExponent(split[2])))
                    .withTrnAmountCardholder(Long.parseLong(separateValue(split[2])))
                    .build();
        }
        return null;
    }

    private String separateValue(final String valueWithExponent) {
        final String trimmedValue = StringUtils.trimToNull(valueWithExponent);
        if (trimmedValue == null || trimmedValue.equals("0")) {
            return "0";
        } else {
            return trimmedValue.substring(1);
        }
    }

    private String separateExponent(final String valueWithExponent) {
        final String trimmedValue = StringUtils.trimToNull(valueWithExponent);
        if (trimmedValue == null || trimmedValue.equals("0")) {
            return "0";
        } else {
            return String.valueOf(trimmedValue.charAt(0));
        }
    }

    private String convertEcbRate(DccOffer dccOffer) {
        if (dccOffer.getEcbUsed()) {
            return "" +
                    converExponentAndValue(dccOffer.getEcbExponent(), dccOffer.getEcbValue()) +
                    "," +
                    (dccOffer.getEcbMarkup() == null ? "0" : dccOffer.getEcbMarkup().toString());
        }

        return "0,0";
    }

    private String converExponentAndValue(Long exponent, Long value) {
        final String valueSafe = nullToZero(value);
        return valueSafe.equals("0") ? "0" : nullToZero(exponent) + valueSafe;
    }

    private String nullToZero(Long val) {
        return val == null ? "0" : val.toString();
    }

    protected Optional<String> convertResponseDccResult(CoreTransaction ctrx) {
        return ctrx.getSpecialProcessing().getDcc().map(Dcc::getDccResult).map(DccResult::getCode);
    }

    protected Optional<String> convertRefundId(CoreTransaction ctrx) {
        return ctrx.getProcessing().getTrnIds().getRefundId().map(Object::toString);
    }

    protected Optional<String> convertResponseDccIdentifier(CoreTransaction ctrx) {
        if (ctrx.is(ProcType.DCC_SALE) || ctrx.is(ProcType.DCC_REFUND) || ctrx.is(ProcType.DCC_PRE_AUTH))
            return ctrx.getSpecialProcessing().getDcc()
                    .map(Dcc::getDccId)
                    .map(Long::toHexString)
                    ;

        return Optional.empty();
    }

    protected Optional<String> convertResponseAsorsFood(CoreTransaction ctrx) {
        return ctrx.getIndustry().getAsorsFood().map(this::convertResponseAsorsFood);
    }

    protected String convertResponseAsorsFood(AsorsFoodIndustry afi) {
        return new StringBuilder()
                .append(afi.getGastroKonto().orElse(0L))
                .append(",")
                .append(afi.getResponseFoodAmount().orElse(0L))
                .append(",0")
                .toString();
    }

    private Optional<String> convertErrorFlagToSpdh(StrongCustomerAuthentication sca) {
        return IssuerRespCodeSCA.SCA_REQUIRED_ONLINE_PIN.equals(sca.getIssuerRespCodeSCA()) ? Optional.of("5")
                : (IssuerRespCodeSCA.SCA_REQUIRED_OFFLINE_PIN.equals(sca.getIssuerRespCodeSCA()) ? Optional.of("6")
                : Optional.empty());
    }

    protected Optional<String> convertCoreResInstallmentToSpdh(InstallmentProc inst) {
        // potvrzeni nebudu posilat do spdh
        if (
                InstallmentIndicator.OFFER.equals(inst.getInstallmentIndicator())
            /*|| InstallmentIndicator.CONFIRMED.equals(inst.getInstallmentIndicator())*/) {

            StringBuilder sb = new StringBuilder()
                    .append("MCI")
                    .append(inst.getType().getCode())
                    .append(inst.getPaymentOption().getCode());

            if(inst.getInstallmentFormat1() != null && inst.getInstallmentFormat1().nonEmpty()) {

                // format 1, version 0
                return Optional.ofNullable(sb
                        .append("0")
                        .append(StringUtils.leftPad(String.valueOf(inst.getInstallmentFormat1().length()), 2, '0')) // NumInstSpecified
                        .append(StringUtils.leftPad(String.valueOf(inst.getInstallmentFormat1().length()), 2, '0')) // NumInstAvailable
                        .append(convertInstMcFormat1sToSpdh(inst.getInstallmentFormat1()))
                        .append(StringUtils.repeat(' ', 2)) // min number of inst
                        .append(StringUtils.repeat(' ', 2)) // max number of inst
                        .append("   ") // advice reason
                        .append("000") // billing currency numeric
                        .append("   ") // billing currency alpha
                        .append(Optional
                                .ofNullable(inst.getReceiptText())
                                .map(String::length)
                                .map(len -> Integer.toString(len))
                                .map(lenStr -> StringUtils.leftPad(lenStr, 4, '0'))
                                .orElse("0000")
                        )
                        .append(Optional.ofNullable(inst.getReceiptText()).orElse(""))
                        .toString());
            }
            else if(inst.getInstallmentFormat2() != null) {
                //format 2, version 1
                return Optional.ofNullable(sb
                        .append("1")
                        .append("01") // NumInstSpecified
                        .append("01") // NumInstAvailable
                        .append(convertInstRateToSpdh(inst.getInstallmentFormat2().getInterestRate()))
                        .append(convertInstAmountToSpdh(inst.getInstallmentFormat2().getInstFee()))
                        .append(convertInstRateToSpdh(inst.getInstallmentFormat2().getAnnualPercRate()))
                        .append(convertInstAmountToSpdh(inst.getInstallmentFormat2().getTotalAmountDue()))
                        .append(StringUtils.repeat(' ', 12)) //first installment amount
                        .append(StringUtils.repeat(' ', 12)) //subsequent installment amounts
                        .append(StringUtils.repeat(' ', 2)) // Number of Installment
                        .append(convertInstNumToSpdh(inst.getInstallmentFormat2().getMinNumOfInst()))
                        .append(convertInstNumToSpdh(inst.getInstallmentFormat2().getMaxNumOfInst()))
                        .append("   ") // advice reason
                        .append("000") // billing currency numeric
                        .append("   ") // billing currency alpha
                        .append(Optional
                                .ofNullable(inst.getReceiptText())
                                .map(String::length)
                                .map(len -> Integer.toString(len))
                                .map(lenStr -> StringUtils.leftPad(lenStr, 4, '0'))
                                .orElse("0000")
                        )
                        .append(Optional.ofNullable(inst.getReceiptText()).orElse(""))
                        .toString());
            } else {
                log.warn(
                        "Installment convert response to spdh was failed. Response installment format1 {}, format2 {}. Return empty installment to SPDH.",
                        inst.getInstallmentFormat1(), inst.getInstallmentFormat2());
                return Optional.empty();
            }
        } else {
            return Optional.empty();
        }
    }

    protected String convertInstNumToSpdh(Integer num) {
        return convertInstIntegerToSpdh(num, 2);
    }

    protected String convertInstRateToSpdh(Integer num) {
        return convertInstIntegerToSpdh(num, 5);
    }

    protected String convertInstIntegerToSpdh(Integer num, int len) {
        try {
            if (num == null) {
                return StringUtils.repeat(' ', len);
            } else {
                final String str = num.toString();
                if (str.length() > len) {
                    log.warn("Installment number is longer than {} -> {}",len,num);
                    return StringUtils.repeat(' ', len);
                }
                return StringUtils.leftPad(str, len, '0');
            }
        } catch (Exception e) {
            return StringUtils.repeat(' ', len);
        }
    }

    protected String convertInstAmountToSpdh(Long num) {
        return convertInstAmountToSpdh(num,12);
    }

    protected String convertInstAmountToSpdh(Long num, int len) {
        try {
            if (num == null) {
                return StringUtils.repeat(' ', len);
            } else {
                final String str = num.toString();
                if (str.length() > len) {
                    log.warn("Installment number is longer than {} -> {}",len,num);
                    return StringUtils.repeat(' ', len);
                }
                return StringUtils.leftPad(str, len, '0');
            }
        } catch (Exception e) {
            return StringUtils.repeat(' ', len);
        }
    }


    protected void validateCoreAcquirerResponseMapping(CoreTransaction ctrx, SpdhResponseMapper mapper, String respDisplay) {
        final String coreAcqCode = ctrx.getProcResult().getAcqRespCode().orElse("--");
        final String spdhRcCode = mapper.getSpdhCodeString();
        if (!coreAcqCode.equals(spdhRcCode)) {
            log.warn("Incorrect mapping between core-acq-rc('{}') and spdh-rc('{}'). ",coreAcqCode,spdhRcCode);
        }

        final String coreAcqText = ctrx.getProcResult().getAcqRespMessage().orElse("--");
        if (!coreAcqText.equals(respDisplay)) {
            log.warn("Incorrect mapping between core-acq-text('{}') and spdh-response-display('{}'). ",coreAcqText,respDisplay);
        }
    }

    protected Optional<String> convertAmountForPartialResponse(CoreResponseCode rc, CoreTransaction ctrx) {
        try {
            if (CoreResponseCode.APPROVED_PARTIAL.equals(rc)
                    || CoreResponseCode.ASORS_FOOD_PARTIAL.equals(rc)
                    || ctrx.getIndustry().getAsorsFood().isPresent()) {
                return Optional.of(String.valueOf(ctrx.getAmounts().getPrimaryValue()));
            } else {
                return Optional.empty();
            }
        } catch (Exception e) {
            log.warn("Can't convert amount for partial amount transaction! ",e);
            return Optional.empty();
        }
    }

    protected Optional<String> convertAvailBalanceForPartialResponse(CoreResponseCode rc, CoreTransaction ctrx) {
        try {
            if (CoreResponseCode.APPROVED_PARTIAL.equals(rc) && PartialAuthIndicator.Forbidden.equals(ctrx.getProcessing().getPartialAuthIndicator())) {
                return Optional.of(String.valueOf(ctrx.getAmounts().getPrimaryValue()));
            } else {
                return Optional.empty();
            }
        } catch (Exception e) {
            log.warn("Can't convert amount for partial amount transaction! ",e);
            return Optional.empty();
        }
    }

    protected Optional<String> convertCurrencyForPartialResponse(CoreResponseCode rc, Amounts amounts) {
        try {
            if (CoreResponseCode.APPROVED_PARTIAL.equals(rc)) {
                return Optional.of(String.valueOf(amounts.getPrimaryCurrency().getCurrencyNumber()));
            } else {
                return Optional.empty();
            }
        } catch (Exception e) {
            log.warn("Can't convert currency for partial amount transaction! ",e);
            return Optional.empty();
        }
    }

    /**
     * Tuhle metodu je potreba pretizit pro specike upravy pro jednotlive SDPH dialekty
     * @param rc
     * @param req
     * @param res
     * @param coreTransaction
     * @param respMapper
     * @return
     */
    protected SpdhMessage finalizeResponseBySpdhDialect(final SpdhRequestContext rc, final SpdhMessage req, final SpdhMessage res,
                                                        final CoreTransaction coreTransaction, final SpdhResponseMapper respMapper, final String translatedResponseMessage) {
        return res;
    }

    public Optional<String> convertAllowedProducts(CoreTransaction ctrx) {
        switch (ctrx.getProcType()) {
            case PRE_AUTH:
            case PURCHASE_ONLINE:
                return ctrx
                        .getIndustry()
                        .getFuel()
                        .flatMap(fuel -> fuel.getProducts().isEmpty() ? Optional.empty() : Optional.of(fuel))
                        .map(fuel -> fuel.getAllowedProducts().isEmpty() ? "00" : convertAllowedProductsDetail(fuel));
            default:
                return Optional.empty();
        }
    }

    protected static String convertAllowedProductsDetail(FuelIndustry fuel) {
        final List<String> allowedIssuerCodes = fuel
                .getAllowedProducts()
                .map(ap -> ap.getGroupIss());
        return fuel
                .getProducts()
                .map(prod -> new ResponseFuelProduct(prod.getGroupIss()))
                .map(rp -> rp.processProduct(allowedIssuerCodes))
                .zipWithIndex()
                .foldLeft(BitMap.ofBitSize(fuel.getProducts().size()), (bitmap, itemTuple)
                        -> itemTuple._1().isEnabled() ? bitmap.withBitSet(itemTuple._2.intValue() +1) : bitmap)
                .toHex()
                ;
    }

    @Override
    public Optional<SpdhResponseMapper> convertResponseCode(CoreResponseCode coreResponseCode) {
        return SpdhResponseCodeMapper.findFromCore(coreResponseCode)
                .map(resp -> resp);
    }

    @Override
    public AcquirerResponseMappingResult mapAcquirerResponse(CoreResponseCode rc, String responseTextLanguage, String networkCode) {
        final SpdhResponseMapper respMapper = convertResponseCode(rc)
                .orElseGet(() -> SpdhResponseCodeMapper.R06);
        final String translatedResponseMessage = respMessageConv.respDisplay(
                networkCode,
                responseTextLanguage,
                respMapper);
        return new AcquirerResponseMappingResult(respMapper.getSpdhCodeString(), translatedResponseMessage);
    }

    protected String convertInstMcFormat1sToSpdh(final List<InstallmentMcFormat1> inst) {
        return inst.map(this::convertInstMcFormat1ToSpdh).foldRight("", (x, xs) -> x + xs);
    }

    protected String convertInstMcFormat1ToSpdh(final InstallmentMcFormat1 inst) {
        try {
            return new StringBuilder()
                    .append(convertInstRateToSpdh(inst.getInterestRate()))
                    .append(convertInstAmountToSpdh(inst.getInstFee()))
                    .append(convertInstRateToSpdh(inst.getAnnualPercRate()))
                    .append(convertInstAmountToSpdh(inst.getTotalAmountDue()))
                    .append(convertInstAmountToSpdh(inst.getFirstInstAmount()))
                    .append(convertInstAmountToSpdh(inst.getSubseqInstAmount()))
                    .append(convertInstNumToSpdh(inst.getNumOfInst()))
                    .toString();
        } catch(Exception e) {
            log.warn("Can't convert installment mc format1 ",e);
            return null;
        }
    }

    protected PreAuth getPreAuth(ProcType procType, String uuid, Amounts amounts, PanEntryMode panEntryMode) {
        if (ProcType.PRE_AUTH.equals(procType)) {
            return PreAuth.fromBuilder()
                    .withPreAuthUuid(uuid)
                    .withAmountHold(amounts.getPrimaryValue())
                    .withAmountCompleted(0L)
                    .withCurrency(amounts.getPrimaryCurrency())
                    .withCardPresent(convertCardPresent(panEntryMode))
                    .build();
        }
        return null;
    }

    private boolean convertCardPresent(PanEntryMode panEntryMode) {
        return panEntryMode.isIn(PanEntryMode.MagneticStripe,
                PanEntryMode.EmvCad,
                PanEntryMode.ContactlessChipCard,
                PanEntryMode.Fallback,
                PanEntryMode.ContactlessMagneticStripe,
                PanEntryMode.ChipReadUnreliable);
    }

    protected Properties convertProperties(SpdhMessage req) {
        return Properties.base();
    }

    protected String stripAccentsByNonCharset(String data, Charset charset) {
        if(charset == null) {
            return CensorUtils.stripAccents(data);
        }
        return data;
    }

    protected ProcType convertProcType(SpdhMessage req, ProcType procType) {
        switch (procType) {
            case PURCHASE_ONLINE:
                return convertSplitPaymentProcType(req, procType);
            case PRE_AUTH:
                return convertPreAuthProcType(req, procType);
            default:
                return procType;
        }
    }

    private ProcType convertSplitPaymentProcType(SpdhMessage req, ProcType procType) {
        return req.getFid(BaseSpdhTag.AmountSplit)
                .filter(splitAmountStr -> !splitAmountStr.isEmpty())
                .map(Integer::valueOf)
                .map(splitAmount -> (splitAmount > 0) ? ProcType.SPLIT_PAYMENT : procType)
                .orElse(procType);
    }

    private ProcType convertPreAuthProcType(SpdhMessage req, ProcType procType) {
        return req.getFid(BaseSpdhTag.PreAuthOperationType)
                .filter(preAuthOpType -> !preAuthOpType.isEmpty())
                .map(preAuthOpType -> preAuthOpType.charAt(0))
                .map(preAuthOpType -> (preAuthOpType.equals('I'))
                        ? ProcType.PRE_AUTH_INCREMENT
                        : ProcType.PRE_AUTH_DECREMENT)
                .orElse(procType);
    }

    protected DccProcIndicator convertDccProcIndicator(String fid) {
        if (fid == null || fid.length() < 2) {
            return DccProcIndicator.UNKNOWN;
        }

        return DccProcIndicator.fromCode(String.valueOf(fid.charAt(1)))
                .orElse(DccProcIndicator.UNKNOWN);
    }

    protected String convertApprovalCode (String origApr,int requiredLength){
        return StringUtils.rightPad(origApr, requiredLength);
    }

    /**
     * Removes tags from being added to core transaction.
     */
    protected EMVChain removeInvoluntaryTags(EMVChain emvChain) {
        return emvChain
                .without(EMVTags.PAN)
                .without(EMVTags.TRACK_2_EQV_DATA);
    }


    @Override
    public CoreTransaction fillFromOrig(CoreTransaction base, OriginalTransaction orig) {
        final String approvalCode = base.getProcResult()
                .getApprovalCode().orElseGet(() -> orig.getProcResult().getApprovalCode().orElse(null));

        final LocalDateTime fixLocalDttm =  base.getTrnDates().getLocalDttm().isAfter(base.getTrnDates().getTransmissionDttm()) ?
                base.getTrnDates().getTransmissionDttm() : base.getTrnDates().getLocalDttm();

        return base
                .withOriginal(orig)
                .withApprovalCode(approvalCode)
                .withLocalDttm(fixLocalDttm)
                .withProcResult(Optional.of(orig.getProcResult()))
                ;
    }

    protected TermHw convertTermHw(SpdhMessage spdh){
        return new TermHw()
                .withTerminalModel(spdh.getFid(BaseSpdhTag.TerminalModel).orElse(null))
                .withTerminalSerialNumber(spdh.getFid(BaseSpdhTag.TerminalSerialNumber).orElse(null))
                .withTerminalPlatform(spdh.getFid(BaseSpdhTag.TerminalPlatform).orElse(null));
    }


    protected Optional<String> convertCoreTrid(String trid) {
        return Optional.empty();
    }

    protected EMVChain removeInvalidTags(EMVChain emvChain, SpdhRequestContext rc) {
        if(emvChain.contains(EMVTags.INTERFACE_DEVICE_SERIAL_NUMBER) &&
                !CensorUtils.isTagAlphanumeric(emvChain.findTlv(EMVTags.INTERFACE_DEVICE_SERIAL_NUMBER).orElse(null), 8)) {
            log.info("{} - Term: {} - Cannot add tag {} - {}. Invalid length (expected {}) or not in alphanumeric format.",
                    rc.getTrid(),
                    Optional.ofNullable(rc.getCad()).map(Cad::getCadProcData).map(CadProcData::getIssuerTerminalId).orElse("TID not found!"),
                    Util.byteArrayToHexString(EMVTags.INTERFACE_DEVICE_SERIAL_NUMBER.getTagBytes()),
                    EMVTags.INTERFACE_DEVICE_SERIAL_NUMBER.getName(), 8);
            return emvChain.without(EMVTags.INTERFACE_DEVICE_SERIAL_NUMBER);
        }
        return emvChain;
    }
}
