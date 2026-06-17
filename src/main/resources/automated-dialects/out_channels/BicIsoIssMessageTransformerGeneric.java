package cz.monetplus.smartswitch.biciso;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cz.monetplus.base24tokens.Token;
import cz.monetplus.base24tokens.TokenBuffer;
import cz.monetplus.base24tokens.TokenFieldKey;
import cz.monetplus.base24tokens.TokenSpec;
import cz.monetplus.base24tokens.specs.*;
import cz.monetplus.biciso.model.Base24Header;
import cz.monetplus.biciso.model.BicIso;
import cz.monetplus.smartswitch.biciso.model.BicIsoIssConstants;
import cz.monetplus.smartswitch.biciso.model.BicIsoIssTransformerProperties;
import cz.monetplus.smartswitch.biciso.model.ReversalReasonRCType;
import cz.monetplus.smartswitch.biciso.spec.BicIsoIssKeys;
import cz.monetplus.smartswitch.biciso.utils.CensorUtilsBicIso;
import cz.monetplus.smartswitch.common.exception.SmartRuntimeException;
import cz.monetplus.smartswitch.common.model.*;
import cz.monetplus.smartswitch.common.model.enums.*;
import cz.monetplus.smartswitch.common.model.industry.asorsfood.AsorsFoodIndustry;
import cz.monetplus.smartswitch.common.model.industry.fare.FareDebtRecoveryType;
import cz.monetplus.smartswitch.common.model.industry.fare.FareOperation;
import cz.monetplus.smartswitch.common.model.industry.otpMolLoyalty.OtpMolLoyaltyIndustry;
import cz.monetplus.smartswitch.common.utils.CensorUtils;
import cz.monetplus.smartswitch.common.utils.SmSwUtils;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.control.Option;

public class BicIsoIssMessageTransformerGeneric implements BicIsoIssMessageTransformer {

    private static final Logger log = LoggerFactory.getLogger(BicIsoIssMessageTransformerGeneric.class);
    protected static final String ZERO4 = "0000";

    protected final Base24TokensTransformer tokensTransformer;
    protected final BodoTransformer bodoTransformer;
    protected final BicIsoIssTransformerProperties properties;

    public BicIsoIssMessageTransformerGeneric(
            Base24TokensTransformer tokensTransformer,
            BodoTransformer bodoTransformer,
            BicIsoIssTransformerProperties properties) {
        super();
        this.tokensTransformer = tokensTransformer;
        this.bodoTransformer = bodoTransformer;
        this.properties = properties;
    }

    @Override
    public BicIso createRequest(final CoreTransaction ctrx) {
        return finalizeByDialect(createRequestBase(ctrx), ctrx);
    }

    public BicIso createRequestBase(final CoreTransaction ctrx) {
        final CadProcData cadProcData = ctrx.getCad().getCadProcData();
        final ProcType procType = ctrx.getProcType();
        return new BicIso(getMtid(ctrx.getProcessing(),ctrx.getProcessing().isResend()),Base24Header.basicPosRequest(), BicIsoIssKeys.f11_stan)
                .withField(BicIsoIssKeys.f3_processingCode, convertProcessingCode(ctrx))
                .withField(BicIsoIssKeys.f4_amount, convertTransactionAmount(ctrx))
                .withField(BicIsoIssKeys.f7_dateTimeTransmission, convertTrnDateTime(ctrx.getTrnDates().getTransmissionDttm()))
                .withField(BicIsoIssKeys.f11_stan, ctrx
                        .getProcessing()
                        .getTrnIds()
                        .getDstStan()
                        .orElseThrow(() -> new SmartRuntimeException(true, "STAN isn't set to transaction!")))
                .withField(BicIsoIssKeys.f12_timeCreate, convertTimeCreate(ctrx))
                .withField(BicIsoIssKeys.f13_dateCreate, convertDateCreate(ctrx))
                .withField(BicIsoIssKeys.f14_cardExpire, convertCardExpire(ctrx))
                .withField(BicIsoIssKeys.f15_settlementDay, convertSettlementDayBase(ctrx))
                .withField(BicIsoIssKeys.f17_caputureDay, convertCaptureDayBase(ctrx))
                .withField(BicIsoIssKeys.f18_mcc, cadProcData.getMcc())
                .withField(BicIsoIssKeys.f22_posEntryMode, getPosDataCode(ctrx))
                .withField(BicIsoIssKeys.f25_posConditionCode, convertPosConditionCodeBase(ctrx))
                .withField(BicIsoIssKeys.f27_authIdRespLen, convertAuthIdRespLen())
                .withField(BicIsoIssKeys.f32_acquiringInstitutCode, ctrx.getAcquirer().getAcquirerId())
                .withField(BicIsoIssKeys.f33_forwardingInstitutIdCode, convertForwardingIdCode(ctrx))
                .withField(BicIsoIssKeys.f35_track2, convertTrack2(ctrx))
                .withField(BicIsoIssKeys.f37_rrn, convertRrn(ctrx))
                .withField(BicIsoIssKeys.f38_approvalCode, convertApprovalCodeBase(ctrx))
                .withField(BicIsoIssKeys.f39_responseCode, convertResponseCodeBase(ctrx))
                .withField(BicIsoIssKeys.f41_cardAcceptorTerminalId, cadProcData.getIssuerTerminalId())//max 8 znaku
                .withField(BicIsoIssKeys.f42_cardAcceptorIdCode, convertCardAcceptorId(ctrx.getCad()))
                .withField(BicIsoIssKeys.f43_cardAcceptorName, convertCardAcceptorLocation(cadProcData))
                .withField(BicIsoIssKeys.f45_track1, convertTrack1(ctrx))
                .withField(BicIsoIssKeys.f48_retailerData, convertRetailerPosData(ctrx))
                .withField(BicIsoIssKeys.f49_currency, ctrx.getAmounts().getPrimaryCurrency().getNumAsString())
                .withField(BicIsoIssKeys.f52_pinBlock, ctrx.getPinData().map(PinData::getPinblockTarget))
                .withField(BicIsoIssKeys.f54_additionalAmounts, convertAdditionalAmountsBase(ctrx))
                .withField(BicIsoIssKeys.f56_bodo, createRequestBodo(ctrx))
                .withField(BicIsoIssKeys.f57_tokens, createRequestTokensLong(ctrx, procType))
                .withField(BicIsoIssKeys.f60_terminalData, convertTerminalData(ctrx.getAcquirer()))
                .withField(BicIsoIssKeys.f61_issuerCategRespData, convertIssuerCategRespData())
                .withField(BicIsoIssKeys.f62_postalCode, convertPostalCode(ctrx, cadProcData))
                .withField(BicIsoIssKeys.f63_tokens, createRequestTokens(ctrx, procType))
                .withField(BicIsoIssKeys.f90_originalData, convertOriginalDataBase(ctrx))
                .withField(BicIsoIssKeys.f95_replacementAmounts, convertReplacementAmounts(ctrx))
                .withField(BicIsoIssKeys.f100_rcvInstitutIdCode, createRcvInstitutIdCode(ctrx))
                .withField(BicIsoIssKeys.f120_termAddresBranch, createTerminalAddressBranch(cadProcData))
                .withField(BicIsoIssKeys.f121_authIndicator, createAuthInticator())
                .withField(BicIsoIssKeys.f123_invoiceData, convertInvoiceDataBase(ctrx))
                .withField(BicIsoIssKeys.f124_batchAndShiftData, convertBatchAndShiftData(ctrx))
                .withField(BicIsoIssKeys.f125_settlementData, convertSettlementDataBase(ctrx))
                .withField(BicIsoIssKeys.f126_preauthtData, convertPreAuthDataBase(ctrx))
                .withField(BicIsoIssKeys.f127_userData, convertUserData(ctrx));
    }

    private Optional<String> convertAdditionalAmountsBase(CoreTransaction coreTransaction) {
        if (coreTransaction.is(ProcType.CASH_BACK)) {
            return convertAdditionalAmountsCashBack(coreTransaction);
        } else if (coreTransaction.isReversal()) {
            return convertAdditionalAmountsReversal(coreTransaction);
        }
        return convertAdditionalAmounts(coreTransaction);
    }

    protected Optional<String> convertSettlementDataBase(CoreTransaction coreTransaction) {
        if (coreTransaction.in(ProcType.PRE_AUTH_DECREMENT, ProcType.PURCHASE_ADJUSTMENT)) {
            return convertSettlementData(DraftCaptureFlag.authorizeOnly);
        }

        return convertSettlementData(coreTransaction
                .getProcessing()
                .getDraftCaptureFlag()
                .orElse(DraftCaptureFlag.authorizeAndCapture));
    }

    protected Optional<String> convertApprovalCodeBase(CoreTransaction coreTransaction) {
        if (coreTransaction.isReversal()) {
            return convertApprovalCodeReversal(coreTransaction);
        } else if (coreTransaction.in(ProcType.PURCHASE_OFFLINE, ProcType.CAPTURE) ||
                (coreTransaction.is(ProcType.REFUND) && !isRefundOnline(coreTransaction.getProcessing()))) {
            return convertApprovalCodePurchaseOffline(coreTransaction);
        } else if (coreTransaction.is(ProcType.PRE_AUTH_COMPLETION)) {
            return convertApprovalCodePreAuthCompl(coreTransaction);
        } else if (coreTransaction.in(ProcType.PRE_AUTH_INCREMENT, ProcType.PRE_AUTH_DECREMENT)) {
            return convertApprovalCodePreAuthIncrementDecrement(coreTransaction);
        }
        return convertApprovalCode(coreTransaction);
    }

    protected Optional<String> convertApprovalCodePreAuthIncrementDecrement(CoreTransaction coreTransaction) {
        return Option.ofOptional(coreTransaction
                        .getProcResult()
                        .getApprovalCode())
                .orElse(Option
                        .ofOptional(getOriginalTransaction(coreTransaction)
                                .getProcResult()
                                .getApprovalCode()))
                .toJavaOptional();
    }

    private Optional<String> convertCaptureDayBase(CoreTransaction coreTransaction) {
        if (coreTransaction.isReversal()) {
            return convertCaptureDayReversal(coreTransaction);
        }
        return convertCaptureDay(coreTransaction);
    }

    private Optional<String> convertResponseCodeBase(CoreTransaction coreTransaction) {
        if (coreTransaction.isReversal()) {
            return convertResponseCodeReversal(coreTransaction);
        } else if (coreTransaction.in(ProcType.PURCHASE_OFFLINE, ProcType.CAPTURE)
                || (coreTransaction.is(ProcType.REFUND) && !isRefundOnline(coreTransaction.getProcessing()))) {
            return convertResponseCodePurchaseOffline(coreTransaction);
        } else if (coreTransaction.is(ProcType.PRE_AUTH_COMPLETION)) {
            return convertResponseCodePreAuthCompl(coreTransaction);
        } else if (coreTransaction.in(ProcType.PRE_AUTH_DECREMENT, ProcType.PURCHASE_ADJUSTMENT)) {
            return convertResponseCodePreAuthDecrement(coreTransaction);
        }
        return convertResponseCode(coreTransaction);
    }

    private Optional<String> convertInvoiceDataBase(CoreTransaction coreTransaction) {
        if (coreTransaction.isReversal()) {
            return convertInvoiceDataReversal(getOriginalTransaction(coreTransaction).getProcessing());
        }
        return convertInvoiceData(coreTransaction);
    }

    private Optional<String> convertPosConditionCodeBase(CoreTransaction coreTransaction) {
        if (coreTransaction.isReversal()) {
            return convertPosConditionCodeReversal(coreTransaction);
        }
        return convertPosConditionCode(coreTransaction);
    }

    private Optional<String> convertPreAuthDataBase(CoreTransaction coreTransaction) {
        if (coreTransaction.is(ProcType.PRE_AUTH)) {
            return convertPreAuthDataForPreAuth(coreTransaction);
        } else if (coreTransaction.is(ProcType.PRE_AUTH_COMPLETION)) {
            return convertPreAuthDataForPreAuthCompl(coreTransaction, getOriginalTransaction(coreTransaction));
        } else if (isReversalOf(coreTransaction, ProcType.PRE_AUTH, ProcType.PRE_AUTH_COMPLETION)) {
            return convertPreAuthDataForReversal(coreTransaction, getOriginalTransaction(coreTransaction));
        }
        return convertPreauthData(coreTransaction);
    }

    private boolean isReversalOf(CoreTransaction coreTransaction, ProcType... reversalOf) {
        if (coreTransaction.is(ProcType.REVERZAL_TECH) || coreTransaction.is(ProcType.REVERZAL_CUSTOMER)) {
            return coreTransaction.getOriginal()
                    .map(transaction -> transaction.in(reversalOf))
                    .orElse(false);
        }
        return false;
    }

    private Optional<String> convertOriginalDataBase(CoreTransaction coreTransaction) {
        if (coreTransaction.isReversal() ||
                coreTransaction.in(ProcType.PRE_AUTH_DECREMENT, ProcType.PRE_AUTH_INCREMENT,
                        ProcType.PURCHASE_ADJUSTMENT, ProcType.CAPTURE)) {
            return createOriginalDataEl(
                    getOriginalTransaction(coreTransaction),
                    coreTransaction.getAcquirer().getAcquirerId(),
                    coreTransaction.getAcquirer().getForwarderId(),
                    coreTransaction);
        }
        return Optional.empty();
    }

    private Optional<String> convertCaptureDay(CoreTransaction coreTransaction) {
        return coreTransaction.getTrnDates().getCaptureDate().map(cdate -> cdate.format(DateTimeFormatter.ofPattern("MMdd")));
    }

    private Optional<String> convertCaptureDayReversal(CoreTransaction coreTransaction) {
        return getOriginalTransaction(coreTransaction)
                .getTrnDates().getCaptureDate().map(cdate -> cdate.format(DateTimeFormatter.ofPattern("MMdd")));
    }

    private Optional<String> convertSettlementDayBase(CoreTransaction coreTransaction) {
        if (coreTransaction.isReversal()) {
            return convertSettlementDayReversal(coreTransaction);
        }
        return convertSettlementDay(coreTransaction);
    }

    private MonthDay convertDateCreate(CoreTransaction coreTransaction) {
        if (properties.isServerDttmForAutoDebtRecoveryEnabled() && isAutoDebtRecovery(coreTransaction.getIndustry())) {
            return MonthDay.from(coreTransaction.getTrnDates().getTransmissionDttm());
        } else {
            if (coreTransaction.isReversal()) {
                return MonthDay.from(getOriginalTransaction(coreTransaction).getTrnDates().getLocalDttm());
            }
            return MonthDay.from(coreTransaction.getTrnDates().getLocalDttm());
        }
    }

    private LocalTime convertTimeCreate(CoreTransaction coreTransaction) {
        if (properties.isServerDttmForAutoDebtRecoveryEnabled() && isAutoDebtRecovery(coreTransaction.getIndustry())) {
            return coreTransaction.getTrnDates().getTransmissionDttm().toLocalTime();
        } else {
            if (coreTransaction.isReversal()) {
                return getOriginalTransaction(coreTransaction)
                        .getTrnDates().getLocalDttm().toLocalTime();
            }
            return coreTransaction.getTrnDates().getLocalDttm().toLocalTime();
        }
    }

    private boolean isAutoDebtRecovery(Industry industry) {
        return industry.getFare()
                .map(fi -> fi.is(FareOperation.DEBT_RECOVERY) && fi.is(FareDebtRecoveryType.AUTO))
                .orElse(false);
    }

    protected Optional<String> convertResponseCode(CoreTransaction coreTransaction) {
        return Optional.empty();
    }

    protected Optional<String> convertResponseCodeReversal(CoreTransaction ctrx) {
        if (ReversalReasonRCType.BASE_24_POS.equals(properties.getResponseCodeType())) {
            switch (ctrx.getProcessing().getIndicators().getMessageReason()) {
                case UNKNOWN: return Optional.of("00");
                case TIMEOUT: return Optional.of("01");
                case AUTH_ERR: return Optional.of("02");
                case POS_DELIV_FAIL: return Optional.of("03");
                case CUST_CANCEL: return Optional.of("08");
                case SYSTEM_MALFUNCTION: return Optional.of("19");
                case SIGN_REJECT:
                case ERR_EMV_PROC: return Optional.of("20");
                default: return Optional.empty();
            }
        } else {
            switch (ctrx.getProcessing().getIndicators().getMessageReason()) {
                case UNKNOWN: return Optional.of("00");
                case TIMEOUT: return Optional.of("68");
                case AUTH_ERR: return Optional.of("40");
                case POS_DELIV_FAIL: return Optional.of("R9");
                case CUST_CANCEL: return Optional.of("17");
                case SYSTEM_MALFUNCTION: return Optional.of("96");
                case SIGN_REJECT:
                case ERR_EMV_PROC: return Optional.of("S0");
                default: return Optional.empty();
            }
        }
    }

    protected Optional<String> convertResponseCodePreAuthCompl(CoreTransaction coreTransaction) {
        return Optional.empty();
    }

    protected Optional<String> convertRrn(CoreTransaction ctrx) {
        return ctrx.getProcessing().getTrnIds().getRrn();
    }

    protected Optional<String> convertResponseCodePurchaseOffline(CoreTransaction coreTransaction) {
        return Optional.of(coreTransaction.getProcResult().getResponseCode().getCode());
    }

    protected Optional<String> convertResponseCodePreAuthDecrement(CoreTransaction ctrx) {
        return Optional.of("64");
    }

    protected Optional<String> convertCardExpire(CoreTransaction ctrx) {
        return ctrx.getCardholder().computeDateExpire();
    }

    protected Optional<String> convertCardAcceptorId(Cad cad) {
        return Optional.empty();
    }

    protected Optional<String> convertPreauthData(CoreTransaction ctrx) {
        return Optional.empty();
    }

    protected Optional<String> createRcvInstitutIdCode(CoreTransaction coreTransaction) {
        if (coreTransaction.isReversal()) {
            return convertRcvInstitutIdCodeReversal(getOriginalTransaction(coreTransaction).getProcResult());
        }
        return Optional.empty();
    }

    protected Optional<String> createAuthInticator() {
        return Optional.empty();
    }

    protected LocalDateTime convertTrnDateTime(LocalDateTime transmissionDttm) {
        return transmissionDttm;
    }

    protected Optional<String> convertInvoiceData(CoreTransaction coreTransaction) {
        return Optional.empty();
    }

    protected Optional<String> convertInvoiceDataReversal(Processing processing) {
        return Optional.empty();
    }

    protected Optional<String> convertPostalCode(CoreTransaction ctrx, CadProcData cadProcData) {
        return cadProcData.getPostalCode();
    }

    /**
     * Pole 54
     */
    protected Optional<String> convertAdditionalAmounts(CoreTransaction ctrx) {
        return Optional.empty();
    }

    protected Optional<String> convertAdditionalAmountsCashBack(CoreTransaction coreTransaction) {
        return coreTransaction.getAmounts()
                .getAddAmnt(AmountType.CASHBACK)
                .map(AdditionalAmount::getAmount)
                .flatMap(this::formatAdditionalAmount);
    }

    protected Optional<String> convertAdditionalAmountsReversal(CoreTransaction coreTransaction) {
        final OriginalTransaction originalTransaction = getOriginalTransaction(coreTransaction);
        if (originalTransaction.is(ProcType.CASH_BACK)) {
            return originalTransaction
                    .getAmounts()
                    .getAddAmnt(AmountType.CASHBACK)
                    .map(AdditionalAmount::getAmount)
                    .flatMap(this::formatAdditionalAmount);
        } else {
            return Optional.empty();
        }
    }

    protected Optional<String> formatAdditionalAmount(Amount amount) {
        try {
            return Optional
                    .of(StringUtils
                            .leftPad(Long
                                    .toString(amount.getValue()), 12, '0'));
        } catch (Exception e) {
            log.error("Additional amount format error! {}",e.getMessage(),e);
            return Optional.empty();
        }
    }

    protected Optional<String> convertTrack1(CoreTransaction ctrx) {
        return ctrx.getCardholder().getTrack1();
    }

    protected Optional<String> convertApprovalCode(CoreTransaction coreTransaction) {
        return coreTransaction.getProcResult().getApprovalCode();
    }

    protected Optional<String> convertApprovalCodeReversal(CoreTransaction coreTransaction) {
        Optional<String> approvalCode = getOriginalTransaction(coreTransaction).getProcResult().getApprovalCode();
        if (!approvalCode.isPresent()) {
            approvalCode = Optional.of("      ");
        }
        return approvalCode;
    }

    protected Optional<String> convertApprovalCodePurchaseOffline(CoreTransaction coreTransaction) {
        return Optional.of(coreTransaction
                .getProcResult().getApprovalCode().orElseGet(SmSwUtils::generateApprovalCode6));
    }

    protected Optional<String> convertApprovalCodePreAuthCompl(CoreTransaction coreTransaction) {
        return convertApprovalCode(coreTransaction);
    }

    protected Optional<String> convertSettlementDayReversal(CoreTransaction coreTransaction) {
        return Optional.of(getOriginalTransaction(coreTransaction)
                .getTrnDates().getTransmissionDttm().format(DateTimeFormatter.ofPattern("MMdd")));
    }

    protected Optional<String> convertSettlementDay(CoreTransaction coreTransaction) {
        return Optional.empty();
    }

    protected Optional<String> convertForwardingIdCode(CoreTransaction ctrx) {
        return Optional.ofNullable(ctrx.getAcquirer().getForwarderId());
    }

    protected Optional<String> convertRcvInstitutIdCodeReversal(ProcResult procResult) {
        return Optional.empty();
    }

    protected BicIso finalizeByDialect(final BicIso model, final CoreTransaction ctrx) {
        return model;
    }


    protected String getMtid(Processing processing, boolean isRepeat) {
        if (isRepeat) {
            switch (processing.getProcType()) {
                case REVERZAL_TECH:
                case REVERZAL_CUSTOMER:
                case PARTIAL_REVERZAL:
                    return "0421";
                case PRE_AUTH_COMPLETION:
                case PURCHASE_OFFLINE:
                case VOICE:
                case FEST_DISCHARGE:
                case REPLENISHMENT_OFFLINE:
                case CAPTURE:
                    return "0221";
                case REFUND:
                    if (isRefundOnline(processing)) {
                        throw new SmartRuntimeException(false, "Unsupported message type for repeat!");
                    } else {
                        return "0221";
                    }
                default:
                    throw new SmartRuntimeException(false, "Unsupported message type for repeat!");
            }
        } else {
            switch (processing.getProcType()) {
                case PRE_AUTH:
                case PRE_AUTH_INCREMENT:
                case BALANCE_INQUIRY:
                case FEST_STATUS:
                    return "0100";
                case CARD_VERIFICATION:
                    if (properties.isCardVerifyMtid0200()) {
                        return "0200";
                    } else {
                        return "0100";
                    }
                case PURCHASE_ONLINE:
                case PRE_AUTH_DECREMENT:
                case PURCHASE_ADJUSTMENT:
                case REPLENISHMENT:
                case CASH_BACK:
                case CASH_ADVANCE:
                case COLLECT:
                case FEST_VALIDATION:
                case FEST_VALIDATION_SAM:
                    return "0200";
                case REVERZAL_TECH:
                case REVERZAL_CUSTOMER:
                case PARTIAL_REVERZAL:
                    return "0420";
                case PRE_AUTH_COMPLETION:
                case PURCHASE_OFFLINE:
                case VOICE:
                case FEST_DISCHARGE:
                case REPLENISHMENT_OFFLINE:
                case CAPTURE:
                    return "0220";
                case REFUND:
                    if (isRefundOnline(processing)) {
                        return "0200";
                    } else {
                        return "0220";
                    }
                default:
                    throw new SmartRuntimeException(false, "Unsupported message type!");
            }
        }
    }

    protected String convertProcessingCode(CoreTransaction coreTransaction) {
        ProcType procType = coreTransaction.getProcType();
        Processing processing = coreTransaction.getProcessing();
        PosInfo posInfo = coreTransaction.getPosInfo();
        if (convertDataFromOriginalTransaciton(coreTransaction)) {
            final OriginalTransaction originalTransaction = getOriginalTransaction(coreTransaction);
            procType = originalTransaction.getProcType();
            processing = originalTransaction.getProcessing();
            posInfo = originalTransaction.getPosInfo();
        }
        return new StringBuilder()
                .append(convertProcessingType(procType, posInfo))
                .append(convertPrimAccount(processing.getAccountType().getPrimary()))
                .append(convertSecAccount(processing.getAccountType().getSecondary()))
                .toString();
    }

    protected boolean convertDataFromOriginalTransaciton(CoreTransaction coreTransaction) {
        return coreTransaction.isReversal();
    }

    protected OriginalTransaction getOriginalTransaction(CoreTransaction coreTransaction) {
        return coreTransaction.getOriginal()
                .orElseThrow(() -> new SmartRuntimeException(false, "No OriginalTransaction was provided in CoreTransaction with ID " + coreTransaction.getTrid()));
    }

    protected Optional<Long> convertTransactionAmount(CoreTransaction ctrx) {
        if (ctrx.is(ProcType.PARTIAL_REVERZAL)) {
            return Optional.of(ctrx
                    .getOriginal()
                    .orElseThrow(() -> new SmartRuntimeException(false, "No original transaction was found for amount conversion in (preauth decrement or partial reverzal)!"))
                    .getAmounts()
                    .getPrimaryValue());
        } else if (ctrx.is(ProcType.PRE_AUTH_DECREMENT)) {
            final Long amntForPreAuthDecrement = ctrx
                    .getProcessing()
                    .getPreAuth()
                    .flatMap(PreAuth::getAmountHold)
                    .orElseThrow(() -> new SmartRuntimeException(false, "Balance for PreAuth decrement is missing!"));
            return Optional.of(amntForPreAuthDecrement);
        } else {
            return Optional.of(ctrx.getAmounts().getPrimaryValue());
        }
    }


    protected String convertProcessingType(ProcType procType, PosInfo posInfo) {
        switch (procType) {
            case REFUND: return "20";
            case CASH_BACK: return "09";
            case COLLECT: return "98";
            case CAPTURE: return "17";
            case BALANCE_INQUIRY: return "31";
            case PURCHASE_ADJUSTMENT:
            case PRE_AUTH_DECREMENT: return "22";
            case CARD_VERIFICATION: return "81";
            case CARD_ACTIVATION: return "29";
            case CARD_DEACTIVATION: return "19";
            case REPLENISHMENT: return "27";
            case FEST_DISCHARGE: return "26";
            case FEST_VALIDATION: return "28";
            case FEST_VALIDATION_SAM: return "32";
            case FEST_STATUS: return "33";
            case REPLENISHMENT_OFFLINE: return "34";
            default:
                return setDefaultValue(procType, posInfo);
        }
    }

    protected String setDefaultValue(ProcType procType, PosInfo posInfo) {
        if (isMoto(posInfo) && !isPreauthOrCompletion(procType)) {
            return "80";
        } else {
            return "00";
        }
    }

    protected boolean isPreauthOrCompletion(ProcType procType) {
        return procType.in(ProcType.PRE_AUTH, ProcType.PRE_AUTH_COMPLETION);
    }

    protected String convertPrimAccount(AccountTypeEnum accountType) {
        return "00";
    }

    protected String convertSecAccount(AccountTypeEnum accountType) {
        return "00";
    }

    protected Optional<String> convertPosConditionCode(CoreTransaction coreTransaction) {
        if (isPosConditionCodePreAuthRelated(coreTransaction.getProcType())) {
            return Optional.of("06");
        } else if (coreTransaction.is(ProcType.PURCHASE_ADJUSTMENT)
                || (coreTransaction.is(ProcType.REFUND) && isMoto(coreTransaction.getPosInfo()))) {
            return Optional.of("00");
        }
        return Optional.of(coreTransaction.getPosInfo().getPosConditionCode().orElse("00"));
    }

    protected Optional<String> convertPosConditionCodeReversal(CoreTransaction coreTransaction) {
        final OriginalTransaction originalTransaction = getOriginalTransaction(coreTransaction);
        if (isPosConditionCodePreAuthRelated(originalTransaction.getProcType())) {
            return Optional.of("06");
        } else {
            return Optional.of(originalTransaction.getPosInfo().getPosConditionCode().orElse("00"));
        }
    }

    protected boolean isPosConditionCodePreAuthRelated(ProcType procType) {
        switch (procType) {
            case PRE_AUTH:
            case PRE_AUTH_COMPLETION:
            case PRE_AUTH_INCREMENT:
            case PRE_AUTH_DECREMENT:
                return true;
            default:
                return false;
        }
    }

    protected Optional<String> convertTrack2(CoreTransaction ctrx) {
        if (dontUseFullTrack2(ctrx.getPosInfo().getPosEntryMode().getPanEntryMode())) {
            return ctrx.getCardholder().computePan()
                    .flatMap(pan -> ctrx.getCardholder().computeDateExpire()
                            .map(expire -> pan + "=" + expire));
        }
        return ctrx.getCardholder().getTrack2Trim();
    }

    private boolean dontUseFullTrack2(PanEntryMode panEntryMode) {
        return PanEntryMode.Manually == panEntryMode ||
                PanEntryMode.Unspecified == panEntryMode;
    }

    protected Optional<Base24TokensTransformer> getTokenTransformer() {
        return Optional.ofNullable(tokensTransformer);
    }

    protected Optional<String> createRequestTokens(CoreTransaction ctrx, ProcType procType) {
        return getTokenTransformer()
                .map(tt -> tt.createRequest(ctrx, procType));
    }

    protected Optional<String> createRequestBodo(CoreTransaction ctrx) {
        if (bodoTransformer != null) {
            try {
                return bodoTransformer.createRequest(ctrx).optionalPack();
            } catch (Exception e) {
                log.warn("{} - BODO request packaging failed! Reason: {}",ctrx.getTrid(),e.getMessage(),e);
                return Optional.empty();
            }
        } else {
            return Optional.empty();
        }
    }

    protected Optional<String> createOriginalDataEl(OriginalTransaction otrx, String acqInstId, String fwdInstId, CoreTransaction ctrx) {
        final StringBuilder sb = new StringBuilder();
        sb.append(getMtid(otrx.getProcessing(),false));
        String stan = otrx.getProcessing()
                .getTrnIds()
                .getDstStan()
                .map(istan -> String.valueOf(istan))
                .map(sStan -> StringUtils.leftPad(sStan, 6, '0'))
                .orElseThrow(() -> new SmartRuntimeException(false, "Original stan not found for reverzal!"));
        sb.append(stan);
        sb.append(otrx.getTrnDates().getLocalDttm().format(DateTimeFormatter.ofPattern("MMddHHmmss")));
        sb.append(StringUtils.leftPad(acqInstId,11,'0'));
        sb.append(StringUtils.leftPad("",11,'0'));
        return Optional.of(sb.toString());
    }

    protected String getPosDataCode(CoreTransaction ctrx) {
        StringBuilder sb =  new StringBuilder();
        sb.append(convertPanEntryMode(ctrx.getPosInfo().getPosEntryMode().getPanEntryMode()));
        sb.append(convertPinEntryMode(ctrx.getPosInfo().getPosEntryMode().getPinEntryMode()));
        return sb.toString();
    }

    protected String convertPanEntryMode(PanEntryMode pem) {
        switch (pem) {
            case Unspecified:		return "00";
            case Manually:			return "01";
            case MagneticStripe:	return "02";
            case BarCode:			return "03";
            case OCR:				return "04";
            case EmvCad:			return "05";
            case ContactlessChipCard:return "07";
            case Fallback:			return "80";
            case ContactlessMagneticStripe:	return "91";
            default: return "00";
        }
    }

    protected String convertPinEntryMode(PinEntryMode pim) {
        switch (pim) {
            case Unspecified : return "0";
            case PINEntryCapability : return "1";
            case NoPINEntryCapability : return "2";
            default:
                return "0";
        }
    }

    protected Optional<String> convertAuthIdRespLen() {
        return Optional.empty();
    }

    protected String convertCardAcceptorLocation(CadProcData cpd) {
        return new StringBuilder()
                .append(fixlen(Optional.ofNullable(cpd.getTermName()).map(CensorUtils::stripAccents).orElse(""), 22))
                .append(fixlen(cpd.getCity().map(CensorUtils::stripAccents).orElse(""), 13))
                .append(fixlen(cpd.getCountry().orElse(""),3))
                .append(fixlen(cpd.getCountry().orElse(""),2))
                .toString()
                ;
    }

    protected Optional<String> convertRetailerPosData(CoreTransaction ctrx) {
        return Optional.of(new StringBuilder()
                .append(fixlen(ctrx.getCad().getCadProcData().getIssuerMerchantId(), 19))
                .append(convertRetailerGroup(ctrx))
                .append(convertRetailerRegion(ctrx))
                .toString());
    }

    protected String convertRetailerGroup(CoreTransaction ctrx) {
        return ZERO4;
    }

    protected String convertRetailerRegion(CoreTransaction ctrx) {
        return ZERO4;
    }


    protected String convertTerminalData(Acquirer acq) {
        return new StringBuilder()
                .append(fixlen(acq.getForwarderId(), 4))
                .append(fixlen(acq.getForwarderId(), 4))
                .append("+000")
                .append("0000")
                .toString();
    }

    protected String convertIssuerCategRespData() {
        return BicIsoIssConstants.P61;
    }

    protected Optional<String> convertBatchAndShiftData(CoreTransaction ctrx) {
        return Optional.empty();
    }

    protected Optional<String> convertPreAuthDataForPreAuth(CoreTransaction ctrx) {
        return Optional.of(new StringBuilder()
                .append("230")
                .append(StringUtils.leftPad(ctrx.getProcessing().getTrnIds().getRrn().orElse("0"),12,'0'))
                .append(StringUtils.repeat(' ', 20))
                .append("  ")
                .append("0")
                .toString());
    }

    protected Optional<String> convertPreAuthDataForPreAuthCompl(CoreTransaction ctrx, OriginalTransaction otrx) {
        return Optional.of(new StringBuilder()
                .append("   ")
                .append(StringUtils.leftPad(otrx.getProcessing().getTrnIds().getRrn().orElse("0"),12,'0'))
                .append(StringUtils.repeat(' ', 20))
                .append("  ")
                .append("0")
                .toString());
    }

    protected Optional<String> convertPreAuthDataForReversal(CoreTransaction ctrx, OriginalTransaction originalTransaction) {
        return Optional.empty();
    }


    protected Optional<String> convertSettlementData(DraftCaptureFlag dcf) {
        return Optional.of(new StringBuilder()
                .append("  ")  // This field corresponds to the RTE.SRV field in the PSTM.
                .append("    ") // This field corresponds to the TRAN.ORIG field in the PSTM.
                .append("    ") // This field corresponds to the TRAN.DEST field in the PSTM.
                .append(dcf.getCode()) // This field corresponds to the TRAN.DFT-CAPTUREFLG field in the PSTM.
                .append(" ") // not used
                .toString());
    }

    public String createTerminalAddressBranch(CadProcData cpd) {
        StringBuilder sb = new StringBuilder("");
        sb.append(fixlen(CensorUtils.stripAccents(cpd.getTermName()),25));
        sb.append("    ");
        return sb.toString();
    }

    protected Optional<String> convertUserData(CoreTransaction ctrx) {
        return Optional.empty();
    }

    protected static String fixlen(String toFix, int len) {
        if (toFix == null || toFix.length() == 0)
            return StringUtils.rightPad("", len, ' ');
        if (toFix.length() == len)
            return toFix;
        else if (toFix.length() < len)
            return StringUtils.rightPad(toFix, len, ' ');
        else
            return toFix.substring(0, len);
    }

    protected Optional<String> convertReplacementAmounts(CoreTransaction ctrx) {
        switch (ctrx.getProcType()) {
            case PRE_AUTH_DECREMENT:
                return formatPreAuthDecrementReplacementAmount(
                        ctrx.getProcType(),
                        ctrx.getAmounts().getPrimary(),
                        ctrx.getProcessing().getPreAuth()
                                .orElseThrow(() -> new SmartRuntimeException(true, "PreAuth hold object is missing for PRE_AUTH_DECREMENT!")));
            case PURCHASE_ADJUSTMENT:
                return ctrx.getAmounts().getAddAmnt(AmountType.REPLACEMENT_AMOUNT)
                        .flatMap(it -> buildAmount(it.getAmount().getValue()));
            default:
                return Optional.empty();
        }
    }

    protected Optional<String> formatPreAuthDecrementReplacementAmount(ProcType procType, Amount amount, PreAuth preAuth) {
        try {
            final long amountHold = preAuth.getAmountHold().orElseThrow(() -> new SmartRuntimeException(true, "PreAuth object doesn't contain AmountHold value."));
            final Long replacementAmount = amountHold - amount.getValue();
            if (replacementAmount < 0) {
                throw new SmartRuntimeException(true, procType.name() + " replacement amount is lesser than 0!");
            }
            return buildAmount(replacementAmount);
        } catch (Exception e) {
            log.error("Pre auth decrement replacement amount format error! {}",e.getMessage(),e);
            return Optional.empty();
        }
    }

    private Optional<String> buildAmount(Long amount){
        return Optional
                .of(StringUtils.rightPad(StringUtils
                        .leftPad(Long
                                .toString(amount), 12, '0'),42,'0'));
    }

    @Override
    public CoreTransaction enrichResponse(CoreTransaction request, BicIso model, String connName) {
        log.trace("{} - Starting enrich trn response!",request.getTrid());
        final CoreResponseCode respCode = model.get(BicIsoIssKeys.f39_responseCode)
                .map(crc -> convertCoreResponseCode(request,crc))
                .orElse(CoreResponseCode.DO_NOT_HONOUR);
        final TokenBuffer tokenBuf = convertTokenBuffer(request.getTrid(), model);
        final boolean replaceTlid = replaceTlidByActionInd(tokenBuf, request);
        final CoreTransaction baseEnrichedCtrx = request
                .builder()
                .transformTrnDates(trnDates -> convertConversionDate(trnDates, tokenBuf))
                .transformAmounts(amounts -> convertAmountsForPartialAuth(amounts, model, request, respCode))
                .transformSpecialProcessing(specProc -> specProc
                        .builder()
                        .transformSca(sca -> convertScaResponse(request.getTrid(), sca, respCode, tokenBuf))
                        .transformInstallment(inst -> processInstallmentResponse(request, inst, tokenBuf))
                        .transformEcommerce(ecomm -> convertEcommerceResponse(
                                ecomm, tokenBuf, request.getCardholder().getCardTypeCode().orElse("")))
                        .transformDigitalWallet(dw -> convertToDigitalWallet(tokenBuf, dw))
                        .transformDcc(dcc -> dcc.withResponseStatus(convertDccResponseStatus(tokenBuf)))
                        .build())
                .transformProcResult(procResult -> procResult
                        .builder()
                        .withState(transactionStateByResponseCode(request, respCode))
                        .withResponseCode(respCode)
                        .withApprovalCode(convertApprovalCodeIfNotSufficientFunds(respCode, model, request))
                        .transformEmvRes(baseEmvRes -> getTokenTransformer()
                                .map(tt -> tt.readEmvFromResponse(baseEmvRes, tokenBuf))
                                .orElse(baseEmvRes))
                        .transformResponseData(responseData -> convertResponseData(responseData, model))
                        .withCustomerInfo(model.get(BicIsoIssKeys.f125_settlementData).orElse(null))
                        .withIssuerRespCode(model.get(BicIsoIssKeys.f39_responseCode).orElse("NONE"))
                        .transformIssuerRespMessages(issuerResponseMessages -> issuerResponseMessages
                                .withCardholderReceipt(respCode.name()))
                        .withTraceId(convertResponseTraceId(tokenBuf, request.getProcResult().getTraceId().orElse(null)))
                        .withIssuerTrxId(convertResponseIssuerId(tokenBuf))
                        .withCardIssuerFiid(convertResponseCardIssuerFiid(request, model))
                        .withCardLogicalNetwork(convertResponseCardLogicalNetwork(request, model))
                        .withErrorMessage(null)
                        .withIssuerPreauthToken(convertIssuerPreauthToken(request, tokenBuf))
                        .withUsedSubSeqRoute(convertSubSeqRoute(tokenBuf))
                        .withMerchantAdviceResponse(convertMerchantAdviceResponse(tokenBuf))
                        .withScheme(convertProcResultScheme(tokenBuf, request.getTrid()))
                        .transformTlid(tlid -> convertTokenHI(tokenBuf, tlid))
                        .withTlidIndicator(replaceTlid
                                ? TlidIndicator.UPDATE
                                : request.getProcResult().getTlidIndicator())
                        .build())
                .transformProcessing(processing -> processing
                        .transformSettlementCtrx(settlementCtrx -> convertSettlementCtrx(tokenBuf, settlementCtrx, request)))
                .withConnName(connName)
                .transformProperties(reqProps -> convertResponseProperties(request, reqProps, model, tokenBuf))
                .transformIndustry(industry -> industry
                        .withOtpMolLoyalty(convertLoyalty(tokenBuf)))
                .transformCardholder(cardHolder ->
                        cardHolder.withIssuerCountry(convertIssuerCountry(tokenBuf, cardHolder.getIssuerCountry().orElse(null))))
                .build();

        return enrichByDialect(
                model
                        .get(BicIsoIssKeys.f56_bodo)
                        .map(bodoBuf -> bodoTransformer.enrichResponse(baseEnrichedCtrx, bodoBuf))
                        .orElse(baseEnrichedCtrx),
                model,
                tokenBuf);
    }

    protected String convertIssuerCountry(TokenBuffer tokenBuf, String defaultValue) {
        return defaultValue;
    }

    private String convertReplacementTlid(TokenBuffer tokenBuffer, boolean replaceTlid) {
        if (replaceTlid) {
            return tokenBuffer.getToken(SpecHI.INSTANCE)
                    .flatMap(tokenHI -> tokenHI.get(SpecHI.LINK_ID))
                    .map(BicIsoIssMessageTransformerGeneric::formatLinkId)
                    .orElse(null);
        }
        return null;
    }

    static String convertTokenHI(TokenBuffer tokenBuffer, String defaultValue) {
        return tokenBuffer.getToken(SpecHI.INSTANCE)
                .flatMap(tokenHI -> tokenHI.get(SpecHI.LINK_ID))
                .map(BicIsoIssMessageTransformerGeneric::formatLinkId)
                .orElse(defaultValue);
    }

    private boolean replaceTlidByActionInd(TokenBuffer tokenBuffer, CoreTransaction request) {
        if (!request.is(CardTypeEnum.MASTERCARD)) {
            return false;
        }
        return tokenBuffer.getToken(SpecHI.INSTANCE)
                .flatMap(tokenHI -> tokenHI.get(SpecHI.ACTION_IND))
                .map(this::isNeededReplaceTlid)
                .orElse(false);
    }

    private boolean isNeededReplaceTlid(String actionInd) {
        return switch (actionInd) {
            case "1", "2", "3" -> true;
            default -> false;
        };
    }

    private static String formatLinkId(String linkId) {
        linkId = StringUtils.stripEnd(linkId, null);
        if (linkId.length() > 30) {
            linkId = StringUtils.left(linkId, 30);
        }
        return StringUtils.isBlank(linkId) ? null : linkId;
    }

    protected Scheme convertProcResultScheme(TokenBuffer tokenBuffer, String trid){
        return Scheme.base();
    }

    protected SettlementCtrx convertSettlementCtrx(TokenBuffer tokenBuffer, SettlementCtrx settlementCtrx, CoreTransaction ctrx){
        return settlementCtrx;
    }

    protected MerchantAdviceResponse convertMerchantAdviceResponse(TokenBuffer tokenBuffer) {
        if (tokenBuffer == null) {
            return MerchantAdviceResponse.base();
        }

        return tokenBuffer.getToken(SpecFN.INSTANCE).map(fnToken ->
                MerchantAdviceResponse.builder()
                        .responseCategoryCode(convertResponseCategoryCode(fnToken.get(SpecFN.RESP_CDE_CAT).orElse("")))
                        .cardStatus(CardStatus.fromCode(fnToken.get(SpecFN.CRD_STAT).orElse("")))
                        .cardRestrictionReasonCode(CardRestrictionReasonCode.fromCode(fnToken.get(SpecFN.RSTRCT_RSN_CDET).orElse("")))
                        .accountStatus(AccountStatus.fromCode(fnToken.get(SpecFN.ACCT_STAT).orElse("")))
                        .retryTime(new RetryTime(fnToken.get(SpecFN.RETRY_TIM).orElse(""), fnToken.get(SpecFN.RETRY_PRD).orElse("")))
                        .additionalResponseInfoCode(convertAdditionalResponseInfoCode(fnToken.get(SpecFN.ADNL_RESP_INFO_CDE).orElse("")))
                        .verificationEntity(VerificationEntity.fromCode(fnToken.get(SpecFN.NAM_VRFN_ENTITY).orElse("")))
                        .build()
        ).orElse(MerchantAdviceResponse.base());
    }

    private ResponseCategoryCode convertResponseCategoryCode (String spec) {
        switch (spec) {
            case "1":
                return ResponseCategoryCode.TRX_CANNOT_APPROVE_WITHOUT_ADD_DETAIL;
            case "2":
                return ResponseCategoryCode.TRX_CANNOT_APPROVE_NOW;
            case "3":
                return ResponseCategoryCode.TRX_CANNOT_APPROVE_AT_ALL;
            case "4":
                return ResponseCategoryCode.TRX_TOKEN_REQUIEREMENTS_NOT_FULFILLED;
            default:
                return ResponseCategoryCode.UNKNOWN;
        }
    }

    private AdditionalResponseInfoCode convertAdditionalResponseInfoCode(String spec) {
        switch (spec) {
            case "C":
                return AdditionalResponseInfoCode.PAYMENT_CANCELLATION;
            case "Q":
                return AdditionalResponseInfoCode.MERCHANT_NOT_QUALIFY_FOR_PRODUCT_CODE;
            case "S":
                return AdditionalResponseInfoCode.SCORE_EXCEEDS_APPLICABLE_THRESHOLD_VALUE;
            case "T":
                return AdditionalResponseInfoCode.TOKEN_REQUIREMENTS_NOT_FULFILLED_FOR_THIS_TOKEN_TYPE;
            case "U":
                return AdditionalResponseInfoCode.NON_RELOADABLE_PREPAID_CARD_PRESENTED;
            case "V":
                return AdditionalResponseInfoCode.SINGLE_USE_VIRTUAL_CARD_NUMBER_PRESENTED;
            case "X":
                return AdditionalResponseInfoCode.MULTI_USE_VIRTUAL_CARD_NUMBER_PRESENTED;
            default:
                return AdditionalResponseInfoCode.UNKNOWN;
        }
    }

    protected OtpMolLoyaltyIndustry convertLoyalty(TokenBuffer tokenBuf) {
        return null;
    }

    protected TrnDates convertConversionDate(TrnDates trnDates, TokenBuffer tokenBuf) {
        return trnDates;
    }

    private String convertSubSeqRoute(TokenBuffer tokenBuf) {
        return tokenBuf.getToken(Spec04.INSTANCE)
                .flatMap(t04 -> t04.get(Spec04.RTE_GRP))
                .map(String::trim)
                .orElse(null);
    }

    protected String convertIssuerPreauthToken(CoreTransaction request, TokenBuffer tokenBuf) {
        return null;
    }

    protected ResponseData convertResponseData(ResponseData in, BicIso model) {
        return in
                .withResponseData(model.get(BicIsoIssKeys.f63_tokens)
                        .map(CensorUtilsBicIso::censorTokens)
                        .orElse(null));

    }

    protected String convertResponseCardIssuerFiid(CoreTransaction request, BicIso model) {
        return model
                .get(BicIsoIssKeys.f61_issuerCategRespData)
                .flatMap(f61 -> extractStringSafe(f61, 0, 4))
                .orElse(null);
    }

    protected String convertResponseCardLogicalNetwork(CoreTransaction request, BicIso model) {
        return model
                .get(BicIsoIssKeys.f61_issuerCategRespData)
                .flatMap(f61 -> extractStringSafe(f61, 4, 8))
                .orElse(null);
    }

    protected Optional<String> extractStringSafe(String str, int start, int end) {
        if (str == null || str.length() < end) {
            return Optional.empty();
        }
        return Optional.of(StringUtils.substring(str, start,end));
    }

    protected String convertResponseTraceId(TokenBuffer tokenBuf, String requestTraceId) {
        return tokenBuf.getToken(Spec20.INSTANCE)
                .flatMap(t20 -> t20.get(Spec20.TRACE_ID))
                .flatMap(BicIsoIssMessageTransformerGeneric::trimRightResponseTraceId)
                .orElse(requestTraceId);
    }

    protected static Optional<String> trimRightResponseTraceId(String traceId) {
        return Optional.ofNullable(StringUtils.stripEnd(traceId, null))
                .filter(StringUtils::isNotBlank);
    }

    protected String convertResponseIssuerId(TokenBuffer tokenBuf) {
        return tokenBuf.getToken(Spec17.INSTANCE)
                .flatMap(t17 -> t17.get(Spec17.TRAN_ID))
                .orElse(null);
    }

    protected Amounts convertAmountsForPartialAuth(Amounts amounts, BicIso model, CoreTransaction ctrx, CoreResponseCode respCode) {
        if (CoreResponseCode.NOT_SUFFICIENT_FUNDS.equals(respCode)) {
            return amounts.withAdditAmount(new AdditionalAmount(
                    AmountType.PARTIAL_AMOUNT,
                    null,
                    model.get(BicIsoIssKeys.f4_amount).map(amountResponse -> new Amount(amountResponse, model
                                    .get(BicIsoIssKeys.f49_currency)
                                    .flatMap(CurrencyCode::fromCurrencyNumber)
                                    .orElseGet(() -> amounts.getPrimary().getCurrency())))
                            .orElseGet(amounts::getPrimary)));
        }

        if(CoreResponseCode.APPROVED_PARTIAL.equals(respCode)) {
            final Amount partialAuthAmount = model.get(BicIsoIssKeys.f4_amount)
                    .map(amountResponse -> new Amount(amountResponse, model
                            .get(BicIsoIssKeys.f49_currency)
                            .flatMap(CurrencyCode::fromCurrencyNumber)
                            .orElseGet(() -> amounts.getPrimary().getCurrency())))
                    .orElseGet(amounts::getPrimary);
            if(PartialAuthIndicator.Forbidden.equals(ctrx.getProcessing().getPartialAuthIndicator())) {
                return amounts.withBalance(partialAuthAmount);
            } else {
                return amounts.withPrimary(partialAuthAmount);
            }
        } else {
            return amounts;
        }
    }

    protected TokenBuffer convertTokenBuffer(String trid, BicIso model) {
        return model.get(BicIsoIssKeys.f63_tokens)
                .flatMap(buf -> getTokenTransformer()
                        .map(tt -> tt.parseResponseTokenBuffer(trid, buf)))
                .orElseGet(TokenBuffer::empty);
    }

    protected String convertApprovalCodeIfNotSufficientFunds(CoreResponseCode respCode, BicIso model, CoreTransaction request) {
        if(CoreResponseCode.NOT_SUFFICIENT_FUNDS.equals(respCode)) {
            return getApprovalCodeValueForNotSufficientFunds();
        }
        return model.get(BicIsoIssKeys.f38_approvalCode)
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .orElseGet(() -> request.getProcResult().getApprovalCode().orElse(null));
    }

    protected String getApprovalCodeValueForNotSufficientFunds() {
        return null;
    }

    protected CoreResponseCode convertCoreResponseCode(CoreTransaction request, String crc) {
        if(BicIsoResponseCode.APPROVED_PARTIAL.getCode().equals(crc) && PartialAuthIndicator.Forbidden.equals(request.getProcessing().getPartialAuthIndicator())) {
            return CoreResponseCode.NOT_SUFFICIENT_FUNDS;
        } else if (isMappingForAsorsFoodPArtialRequired(request, crc)) {
            return CoreResponseCode.ASORS_FOOD_PARTIAL;
        } else {
            return BicIsoResponseCode
                    .fromCode(crc)
                    .flatMap(BicIsoResponseCode::fromBicIsoResponseCode)
                    .orElse(CoreResponseCode.DO_NOT_HONOUR)
                    ;
        }
    }

    private boolean isMappingForAsorsFoodPArtialRequired(CoreTransaction request, String crc) {
        return request
                .getIndustry()
                .getAsorsFood()
                .map(AsorsFoodIndustry::isPartialAuthUsed)
                .orElse(false)
                && BicIsoResponseCode.APPROVED.getCode().equals(crc);
    }


    private StrongCustomerAuthentication convertScaResponse(String trid, StrongCustomerAuthentication sca, CoreResponseCode respCode, TokenBuffer tb) {
        if(CoreResponseCode.PIN_REQUIRED.equals(respCode)) {
            return tb.getToken(Spec04.INSTANCE)
                    .flatMap(t04 -> t04.get(Spec04.ERR_FLG))
                    .map(errFlg -> setSCAFromErrorFlag(trid, errFlg))
                    .map(sca::withIssuerRespCodeSCA)
                    .orElse(sca);
        }
        return sca;
    }

    private String convertDccResponseStatus(TokenBuffer tb) {
        return tb.getToken(SpecS2.INSTANCE)
                .flatMap(t04 -> t04.get(SpecS2.DCC_STAT))
                .orElse(null);
    }

    private IssuerRespCodeSCA setSCAFromErrorFlag(String trid, String code) {
        if(code.equals("5")) {
            return IssuerRespCodeSCA.SCA_REQUIRED_ONLINE_PIN;
        } else if(code.equals("6")) {
            return IssuerRespCodeSCA.SCA_REQUIRED_OFFLINE_PIN;
        }
        log.info("{} - Error Flag not responding with SCA BicIso spec (allowed 5,6 value), response Error Flag {}", trid, code);
        return IssuerRespCodeSCA.NONE;
    }


    public CoreTransaction enrichByDialect(CoreTransaction ctrx, BicIso model, TokenBuffer tokenBuf) {
        return ctrx;
    }

    protected Amount convertResponsePartialAmount(CoreResponseCode rc, BicIso model, Amount origAmount) {
        if (!CoreResponseCode.APPROVED_PARTIAL.equals(rc))
            return origAmount;
        return Amount.fromNullable(model.get(BicIsoIssKeys.f4_amount).orElse(null), origAmount.getCurrency())
                .orElse(origAmount)
                ;
    }

    protected TransactionState transactionStateByResponseCode(CoreTransaction ctrx, CoreResponseCode arc) {
        switch (arc) {
            case APPROVED:
            case APPROVED_PARTIAL:
            case ASORS_FOOD_PARTIAL:
                return TransactionState.ACCEPTED;
            case ERROR:
            case FORMAT_ERROR:
                return TransactionState.FAIL;
            default:
                return TransactionState.DECLINED;
        }
    }

    protected Optional<String> createRequestTokensLong(CoreTransaction ctrx, ProcType procType) {
        return Optional.empty();
    }

    protected Ecommerce convertEcommerceResponse(Ecommerce ecommerce, TokenBuffer tb, String cardType) {
        return ecommerce;
    }

    protected InstallmentProc processInstallmentResponse(CoreTransaction request, InstallmentProc inst, TokenBuffer tokenBufGpeGen2) {

        if(inst.is(InstallmentIndicator.POSSIBLE)) {
            if (tokenBufGpeGen2.getToken(SpecFD.INSTANCE).isPresent()) {

                final String receiptData = tokenBufGpeGen2.getToken(SpecSM.INSTANCE)
                        .flatMap(tSM -> tSM.get(SpecSM.BUF))
                        .orElse("");

                final Optional<Token<SpecFD>> installmentToken = tokenBufGpeGen2.getToken(SpecFD.INSTANCE);

                final InstallmentPaymentOption installmentPaymentOption = installmentToken
                        .flatMap(tFD -> tFD.get(SpecFD.PMNT_OPT))
                        .map(this::convertInstallmentPaymentOption)
                        .orElse(InstallmentPaymentOption.UNKNOWN);

                final InstallmentType installmentType = installmentToken
                        .flatMap(tFD -> tFD.get(SpecFD.INSTL_PLAN_TYP))
                        .map(this::convertInstallmentType)
                        .orElse(InstallmentType.UNKNOWN);

                final String version = installmentToken
                        .flatMap(tFD -> tFD.get(SpecFD.INSTL_OPT))
                        .orElse(null);

                final Tuple2<List<InstallmentMcFormat1>, InstallmentMcFormat2> data = convertInstallmentData(request.getTrid(), version, installmentToken);

                final CurrencyCode currencyChldrBill = installmentToken
                        .flatMap(tFD -> tFD.get(SpecFD.ISS_CRNCY_CDE))
                        .flatMap(CurrencyCode::fromCurrencyNumber)
                        .orElseGet(() -> request.getAmounts().getAmountBillCurrency()
                                .orElse(request.getAmounts().getPrimaryCurrency()));

                return InstallmentProc.fromBuilder()
                        .withPaymentOption(installmentPaymentOption)
                        .withType(installmentType)
                        .withInstallmentIndicator(InstallmentIndicator.OFFER)
                        .withReceiptText(receiptData)
                        .withInstMcFormat1(data._1())
                        .withInstMcFormat2(data._2())
                        .withCardholderCurrency(currencyChldrBill)
                        .build()
                        ;
            }
            return InstallmentProc.noOffer();
        }
        return InstallmentProc.none();
    }

    protected Tuple2<List<InstallmentMcFormat1>, InstallmentMcFormat2> convertInstallmentData(String trid, String version, Optional<Token<SpecFD>> installmentToken) {
        if(version == null) {
            return Tuple.of(List.empty(), InstallmentMcFormat2.none());
        }
        if("0".equals(version)) {
            return createMcFormat1(trid, installmentToken);
        } else if ("1".equals(version)) {
            return createMcFormat2(installmentToken);
        }
        return Tuple.of(List.empty(), InstallmentMcFormat2.none());
    }

    protected Tuple2<List<InstallmentMcFormat1>, InstallmentMcFormat2> createMcFormat2(Optional<Token<SpecFD>> installmentToken) {
        final InstallmentMcFormat2 mcFormat2 = InstallmentMcFormat2.fromBuilder()
                .withMinNumOfInst(installmentToken
                        .flatMap(tFD -> tFD.get(SpecFD.MIN_NUM_INSTL_INT))
                        .orElse(0))
                .withMaxNumOfInst(installmentToken
                        .flatMap(tFD -> tFD.get(SpecFD.MAX_NUM_INSTL_INT))
                        .orElse(0))
                .withInterestRate(installmentToken
                        .flatMap(tFD -> tFD.get(SpecFD.INTRST_RATE_INT))
                        .orElse(0))
                .withInstFee(installmentToken
                        .flatMap(tFD -> tFD.get(SpecFD.INSTL_FEE))
                        .map(Long::valueOf)
                        .orElse(0L))
                .withAnnualPercRate(installmentToken
                        .flatMap(tFD -> tFD.get(SpecFD.ANNUAL_PCTG_RATE_INT))
                        .orElse(0))
                .withTotalAmountDue(installmentToken
                        .flatMap(tFD -> tFD.get(SpecFD.TTL_AMT_DUE))
                        .map(Long::valueOf)
                        .orElse(0L))
                .withNumOfInst(installmentToken
                        .flatMap(tFD -> tFD.get(SpecFD.NUM_INSTL_INT))
                        .orElse(0))
                .build();
        return Tuple.of(List.empty(), mcFormat2);
    }

    protected Tuple2<List<InstallmentMcFormat1>, InstallmentMcFormat2> createMcFormat1(String trid, Optional<Token<SpecFD>> installmentToken) {
        return installmentToken
                .flatMap(tFD -> tFD.get(SpecFD.MC_INSTL_RESP_DATA))
                .map(data -> convertMcFormat1(trid, data))
                .orElse(Tuple.of(List.empty(), InstallmentMcFormat2.none()))
                ;
    }

    protected Tuple2<List<InstallmentMcFormat1>, InstallmentMcFormat2> convertMcFormat1(String trid, String mcData) {
        final String mcDataWithoutHead = mcData.substring(16);

        final List<InstallmentMcFormat1> installmentMcFormat1s = Optional.of(mcDataWithoutHead)
                .map(f1 -> f1.split("(?<=\\G.{64})"))
                .map(List::of)
                .filter(this::lastLen)
                .map(list -> size(trid, list))
                .map(format1List -> format1List
                        .filter(data -> StringUtils.trimToNull(data) != null)
                        .map(this::convertInstallmentMcFormat1))
                .orElse(List.empty());

        return Tuple.of(installmentMcFormat1s, InstallmentMcFormat2.none());
    }

    protected InstallmentMcFormat1 convertInstallmentMcFormat1(String format1) {
        InstallmentMcFormat1Builder format1Build = InstallmentMcFormat1Builder.none();

        try {
            format1Build.withInterestRate(parseNullableIntegerSafe(format1, 0, 5));//5
            format1Build.withInstFee(parseNullableLongSafe(format1, 5, 17)); //12
            format1Build.withAnnualPercRate(parseNullableIntegerSafe(format1 ,17, 22)); //5
            format1Build.withTotalAmountDue(parseNullableLongSafe(format1, 22, 34)); //12
            format1Build.withFirstInstAmount(parseNullableLongSafe(format1, 34, 46)); //12
            format1Build.withSubseqInstAmount(parseNullableLongSafe(format1, 46, 58)); //12
            format1Build.withNumOfInst(parseNullableIntegerSafe(format1,58, 60)); //2

        } catch(Exception e) {
            log.warn("Converting response installment mc format1 fail! ",e);
            return null;
        }

        return format1Build.build();
    }

    protected Long parseNullableLongSafe(String full, int from, int to) {
        try {
            return Long.parseLong(full.substring(from,to));
        } catch (Exception e) {
            return null;
        }
    }

    protected Integer parseNullableIntegerSafe(String full, int from, int to) {
        try {
            return Integer.parseInt(full.substring(from,to));
        } catch (Exception e) {
            return null;
        }
    }

    protected boolean lastLen(List<String> list) {
        if(list.last().length() != 64) {
            log.warn("Bad parse of data installment mc format 1. Last element is not equals 64 bytes");
            return false;
        }
        return true;
    }

    protected List<String> size(String trid, List<String> list) {
        if(list.size() > 12) {
            log.warn("{} - There was found {} installment options. Max 12", trid, list.size());
            return list.subSequence(12);
        }
        return list;
    }

    protected InstallmentPaymentOption convertInstallmentPaymentOption(String insPayOpt) {
        return InstallmentPaymentOption.fromCode(insPayOpt);
    }

    protected InstallmentType convertInstallmentType(String insTyp) {
        return InstallmentType.fromCode(insTyp);
    }

    protected boolean isRefundOnline(Processing proc) {
        switch (proc.getIndicators().getProcessingMethodIndicator()) {
            case OFFLINE:
            case ONLINE_WITH_OFFLINE_FALLBACK:
                return false;
            case ONLINE:
                return true;
            case DEFAULT:
            case UNSUPPORTED:
            default:
                return properties.isRefundOnline();
        }
    }

    protected Properties convertResponseProperties(CoreTransaction coreTransaction, Properties reqProps, BicIso model, TokenBuffer tokenBuf) {
        return reqProps;
    }

    protected DigitalWallet convertToDigitalWallet(TokenBuffer tokenBuf, DigitalWallet wallet) {
        return fillWalletPanAndExpFromToken(tokenBuf, wallet)
                .transformPar(par -> convertPar(tokenBuf, par));
    }

    private String convertPar(TokenBuffer tokenBuffer, String defaultValue) {
        final String tokenSOPar = getTokenValue(tokenBuffer, SpecSO.INSTANCE, SpecSO.PAR, true);
        return tokenSOPar != null ? tokenSOPar : defaultValue;
    }

    protected DigitalWallet fillWalletPanAndExpFromToken(TokenBuffer tokenBuffer, DigitalWallet wallet) {
        return fillWalletPanAndExpFromTokenS8(tokenBuffer, wallet)
                .orElseGet(() -> fillWalletPanAndExpFromTokenSC(tokenBuffer, wallet)
                        .orElse(wallet));
    }

    protected Optional<DigitalWallet> fillWalletPanAndExpFromTokenSC(TokenBuffer tokenBuffer, DigitalWallet wallet) {
        if (isAllowedIndicatorSC(tokenBuffer)) {
            final String pan = getTokenValue(tokenBuffer, SpecSC.INSTANCE, SpecSC.PAN_TKN_DATA, true);
            final String exp = getTokenValue(tokenBuffer, SpecSC.INSTANCE, SpecSC.PAN_TKN_EXP_DAT, true);
            return Optional.of(wallet.withEmbossedPanAndExpiration(pan, exp));
        }
        return Optional.empty();
    }

    protected boolean isAllowedIndicatorSC(TokenBuffer tokenBuffer) {
        return TokentDataType.PAN.equals(getTokenValue(tokenBuffer, SpecSC.INSTANCE, SpecSC.PAN_TKN_IND));
    }

    protected Optional<DigitalWallet> fillWalletPanAndExpFromTokenS8(TokenBuffer tokenBuffer, DigitalWallet wallet) {
        return Optional.empty();
    }

    protected <S extends TokenSpec<S>> String getTokenValue(TokenBuffer buffer, S spec, TokenFieldKey<S, String> key) {
        return getTokenValue(buffer, spec, key, false);
    }

    protected <S extends TokenSpec<S>> String getTokenValue(TokenBuffer buffer, S spec, TokenFieldKey<S, String> key, boolean trimOutput) {
        final String result = buffer.getToken(spec)
                .flatMap(token -> token.get(key))
                .orElse(null);
        if (trimOutput && result != null) {
            return StringUtils.trimToNull(result);
        }
        return result;
    }

    protected boolean isMoto(PosInfo posInfo) {
        return CardholderPresentIndicator.notPresentMailOrFax.equals(posInfo.getCardHolderPresentIndicator());
    }

    protected static String replaceCharacters(String string, String replacement, int position) {
        //set the correct position (index starts at 0)
        position--;
        StringBuilder builder = new StringBuilder(string);
        builder.replace(position, position + replacement.length(), replacement);
        return builder.toString();
    }

}
