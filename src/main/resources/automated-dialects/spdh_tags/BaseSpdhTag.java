package cz.monetplus.smartswitch.spdh.tags;

import cz.monetplus.spdh.commons.SpdhTag;

public enum BaseSpdhTag implements SpdhTag {

	/**
	 * VAS transaction code
	 */
	VasTransactionCode("A"),
	/**
	 * Optinal data. (a)
	 */
	OptionalData("a"),
	/**
	 * PIN block. (b)
	 */
	PinBlock("b"),
	/**
	 * Amount1. (B)
	 */
	Amount1("B"),
	/**
	 * Amount2. (C)
	 */
	Amount2("C"),
	
	/**
	 * Account type - Typ uctu (D)
	 */
	AccountType("D"),
	
	/**
	 * Retailer ID (d)
	 * <p>The Retailer ID field contains the ID assigned to a merchant group by
	 * organizations such as MasterCard, VISA, or American Express.</p>
	 */
	RetailerId("d"),
	
	/**
	 * POSConditionCode. (e)
	 */
	POSConditionCode("e"),
	/**
	 * Autorizacni kod. (F)
	 */
	ApprovalCode("F"),
	/**
	 * Text k zobrazeni na terminalu. (g)
	 */
	ResponseDisplay("g"),
	
	/**
	 * Sekvencni cislo zpravy. (h)
	 */
	SequenceNumber("h"),
	/**
	 * Puvodni sekvencni cislo zpravy. (i)
	 */
	SequenceNumberOriginal("i"),
	/**
	 * Dostupny zustatek na uctu. (J)
	 */
	AvailableBalance("J"),
	/**
	 * Soucty uzaverky. (l)
	 */
	BatchTotals("l"),
	/**
	 * Transportni klic. (M)
	 */
	TransportKey("M"),
	/**
	 * Pouziva se pro prenos cisla uctenky. (5-ti ciselny retezec) (N)
	 */
	CustomerID("N"),
	/**
	 * DraftCaptureFlag. (P)
	 */
	DraftCaptureFlag("P"),
	
	/**
	 * EncryptedTrack2. (r)
	 */
	EncryptedTrack2("r"),
	
	/**
	 * Driver ID, ale jen pro Etoll na Paywell. (S)
	 */
	EtollDriverId("S"),
	
	/**
	 * Cislo originalni uctenky. (S)
	 */
	OriginalReceiptCode("S"),
	
	/**
	 * Dalsi text na transakci - Cardholder receipt text (s)
	 */
	TransactionDescription("s"),

	/**
	 * Pouziva se pro dalsi VS
	 */
	InvoiceNumberOriginal("T"),
	
	/**
	 * LanguageTable. -  (U)
	 */
	LanguageTable("U"),
	/**
	 * Track2. (q)
	 */
	Track2("q"),
	/**
	 * Je nutno kopirovat do odpovedi. Vyuzivano terminalem k parovani transakci. (Q)
	 */
	Echo("Q"),
	/**
	 * Track1 (iso7811/2 tabulka 1, omezena tabulka znaku). (2)
	 */
	Track1("2"),
	/**
	 * Binarni data cipove transakce zakodovane do base64. Industry type = "CT" Industry Data = BASE64 binarnich dat
	 * cipove transakce (4)
	 */
	IndustryData("4"),
	/**
	 * Seznam produktovych SFID v ramci FID 6. (6)
	 */
	ProductSFIDS6("6"),
	/**
	 * Seznam zakaznickych SFID v ramci FID 9. (9)
	 */
	CustomerSFIDS9("9"),

	/**
	 * Group of data elements, identifying transaction (60)
	 */
	AdditionalPointOfServiceData("60"),

	/**
	 * Cas vzniku offline transakce ve formaty yyMMddHHmmss. (6A)
	 */
	OfflineTxTime("6A"),

	/**
	 * Offline Authentication Field Identifier (FID '6a')
	 */
	OfflineAuthentication("6a"),
	
	/**
	 * CVD/CVC/CVV/CVV2 (6B)
	 */
	ManualCvd("6B"),

	/**
	 * refund_id
	 */
	RefundId("6c"),
	
	/**
	 * Ciselny kod meny. (6I)
	 */
	CurrencyCode("6I"),
	/**
	 * POSEntryMode. (6E)
	 */
	POSEntryMode("6E"),

	/**
	 * Providing additional information. (6n)
	 */
	ErrorFlag("6n"),
	
	/**
	 * EMV Request data (6O)
	 */
	EmvRequestData("6O"),
	
	/**
	 * Addtional EMV Request data (6P)
	 */
	EmvRequestDataAdditional("6P"),
	
	/**
	 * EMV supplementary request data pro cless (6q)
	 */
	EmvSupplRequestCless("6q"),
	
	/**
	 * EMV Response Data (6Q)
	 */
	EmvResponseData("6Q"),
	
	/**
	 * EMV Reversal Data/EMV Additional Response Data (6R)
	 */
	EmvAdditionalResponseData("6R"),

	/**
	 * Terminal Platform received from Terminal (6r)
	 */
	TerminalPlatform("6r"),
	
	/**
	 * Terminal Serial Number received from Terminal from Printec (6S)
	 */
	TerminalSerialNumber("6S"),

	/**
	 * *Terminal Model received from Terminal (6s)
	 */
	TerminalModel("6s"),
	
	/**
	 * Terminal Firmware received from Terminal (6F)
	 */
	Firmware("6F"),

	/**
	 * Initial vector received from Terminal (6v)
	 */
	IV("6v"),

	/**
	 * EMV additional request data
	 */
	EmvRequestAdditional2("8q"),

	/**
	 * Request for card validation
	 */
	CardValidationRequired("8a"),

	/**
	 * Selected card brand for multi brand cards
	 */
	SelectedScheme("8b"),

	/**
	 * Used Card Brand name
	 */
	CardBrandScheme("8c"),

	/**
	 * Amount of Surcharge operation
	 */
	SurchargeAmount("8B"),

	/**
	 * Vat for Surcharge operation
	 */
	SurchargeVatAmount("8C"),

	/**
	 * Amount split for secondary amount
	 */
	AmountSplit("8t"),

	/**
	 * MineSec SoftPOS transaction data
	 */
	SoftPosData("8s"),

	/**
	 * Indicator if card brand schema is selected manually or in different mode
	 */
	selectedSchemeMode("8S"),

	/**
	 * KSN for DUKPT (6T)
	 */
	KSN("6T"),
	
	/**
	 * KSN DUKPT additional info (6T)
	 */
	KSN_ADD_INFO("6t"),

	/**
	 * Struktura informaci o terminalu pro PayWell
	 */
	PosIfoPayWell("6X"),

	/**
	 * Point of service for MO/TO transaction support
	 */
	PosInfoCsob("6X"),
	
	/**
	 * Stav tachometru vozidla. (90)
	 */
	VehicleTachometer("90"),
	/**
	 * Cislo ridice. (91)
	 */
	DriverNo("91"),
	/**
	 * Cislo vozidla. (92)
	 */
	VehicleCode("92"),
	/**
	 * Kod obchodnika, textova reprezentace cisla o fixni delce 2. (93)
	 */
	MerchantCode("93"),
	/**
	 * Datum a cas vzniku uctenky ve formatu yyyyMMddHHmm. (94)
	 * A2 - Nevyuzivane
	 */
	ReceiptCreation("94"),
	/**
	 * Unencrypted ID - Fuel. (94)
	 */
	UnencryptedId("94"),
	/**
	 * Identifikacni kod prodavajiciho. (95)
	 * A2 - Nevyuzivane
	 */
	SellerCode("95"),
	/**
	 * OrderId - Fuel (95)
	 */
	OrderId("95"),
	/**
	 * Identifikacni kod darku. (96)
	 */
	PresentCode("96"),

	/**
	 * Vehicle tag - Fuel. (96)
	 */
	VehicleTag("96"),
	
	/**
	 * Carovy kod 1 (9a)
	 */
	Barcode1("9a"),

	/**
	 * HU close loop cards (9a)
	 */
	SzepCloseLoop("9a"),

	/**
	 * Indikuje typ formátu pinblock 0 - Format 0, 1 - Format 1
	 */
	PinblockFormat("9B"),

	/**
	 * Puvodni amount z komplementace na nulu
	 */
	PreauthCompletionAmoundHold("9B"),
	
	/**
	 * Carovy kod 1 (9b)
	 */
	Barcode2("9b"),

	/**
	 * Carovy kod 1 (9b)
	 */
	Installment("9b"),

	/**
	 * Payter Issuer transaction id (9b)
	 */
	PayterIssuerTrxId("9b"),

	/**
	 * Indikuje zda se jedna o creditni a nebo debetni transakci (9c)
	 */
	creditIndicator("9c"),

	/**
	 * Spropitne
	 */
	amountTip("9c"),

	/**
	 * Processor token
	 */
	ProcessorToken("9d"),

	/**
	 * Core transaction id (9e)
	 */
	CoreTRID("9e"),

	/**
	 * Issuer transaction id (9E)
	 */
	IssuerTrxId("9E"),


	/**
	 * Hlavicka komoditni transakce. Struktura: X(8) [XID - identifikator platby] + X(4) [celkovy pocet polozek v
	 * platbe] (9H)
	 */
	CommodityHeader("9H"),
	/**
	 * Data komoditni transakce. Big endian binarni struktura zakodovana v BASE64. Struktura binarnich dat:
	 * 2B(productGroup) + 1B(VAT group) + 4B(amount * 1000) + 4B(unit price * 1000) + 4B(total price * 100) (9D)
	 */
	CommodityData("9D"),
	
	/**
	 * Text na listecek s diakritkou zakodovany jako base64
	 */
	LocaleBase64Text("9j"),

	/**
	 * Specialni tag ktery nese udaje o amountu na specialni produkty
	 */
	AdditionalAmountTag("9G"),

	/**
	 * Gastro data pro ASORS FOOD (9G)
	 */
	AsorsFoodData("9G"),

	/**
	 * Celkova blokovana castka pred autorizace
	 */
	PreAuthHoldAmount("9J"),

	/**
	 * Indikator ze jsou product data formatovana podle lukoilu
	 */
	CommodityLukoilInd("9L"),

	/**
	 * Terminal_id puvodniho terminal pro dohledani orig. transakce
	 */
	TerminalId("9m"),

	/**
	 * Token do response pred autorizace, ktery zastupuje PAN
	 */
	PreAuthToken("9n"),
	
	/**
	 * Indikuje povoleni partial amountu
	 */
	PartialAmountInd("9P"),
	
	/**
	 * cislo kterym se vynasobi mnozstvi
	 */
	CommodityPrecision("9P"),

	/**
	 * druhy PAN
	 */
	SecondPan("9Q"),

	/**
	 * Alternative account number (IBAN)
	 */
	AltAccountNumber("9q"),
	
	/**
	 * Segment komoditni transakce. Struktura: X(4) [index prvni polozky v dane transakci] + X(4) [pocet polozek v dane
	 * transakci] (9S)
	 */
	VariableSymbol("9S"),
	
	/**
	 * Segment komoditni transakce. Struktura: X(4) [index prvni polozky v dane transakci] + X(4) [pocet polozek v dane
	 * transakci] (9S)
	 */
	CommoditySegment("9S"),
		
	/**
	 * Dalsi text na transakci - Card acceptor receipt text (s)
	 */
	TransactionDescriptionCA("9s"),

	/**
	 * Issuer TID (9t)
	 */
	IssuerTID("9t"),

	/**
	 * Pan (9u)
	 */
	Pan("9u"),

	/**
	 * Rid
	 */
	Rid("9r"),
	
	/**
	 * Cislo uctenky terminalu. (6 cisel zarovnanych zleva nulama) (9R)
	 */
	ReceiptCode("9R"),
	/**
	 * Cislo originalni uctenky kasy. (textovy retezec) (9T)
	 */
	OriginalPosReceiptCode("9T"),
	/**
	 * Verze projektu. Pro Benzinu plati hodnoty 15 a 20. (9V)
	 */
	ProjectVersion("9V"),
	
	/**
	 * (9U) - Jakym jazykem se ma kodovat response odpoved (cz,en,sk)
	 */
	LanguageCode("9U"),
	/**
	 * Produktova data, od verze 30 jsou komodity posilany ve FIDU
	 */
	ProductData("9p"),
	
	/**
	 * (9Z) - Prazdne data ktera se maji dat na konec (Docasny bug ve smartshop terminalu s parsovanim BASE64)
	 * 
	 */
	ZeroData("9z"),

	/**
	 * Typ zmenny pred autorizace; I - increment; D - decrement
	 */
	PreAuthOperationType("9i"),

	/**
	 * DCC identifikator
	 */
	DccIdentifier("9I"),

	/**
	 * DCC offer
	 */
	DccOffer("9O"),

	/**
	 * DCC request
	 */
	DccRequest("9Q"),

	/**
	 * DCC status/result
	 */
	DccResult("9R"),

	/**
	 * DCC refund data
	 */
	DccExtension("9F"),

	/**
	 * DPar
	 */
	Par("9f"),

	/**
	 * TraceId
	 */
	TraceId("9g"),

	/**
	 * Monet token
	 */
	MonetToken("9k"),

	Bar("9K"),

	ResponseData("9M"),

	ProcessorId("9h"),

	ResponseDataMap("9o"),

	GainedBonusPoints("9w"),

	GainedBonusPointsFromBonusUsage("9W")
	;

	private final String tag;
	
	private BaseSpdhTag(String tag) {
		this.tag = tag;
	}

	@Override
	public String getTag() {
		return tag;
	}

}
