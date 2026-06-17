package cz.monetplus.smartswitch.spdh.tags;

import cz.monetplus.spdh.commons.SpdhTag;

/**
 * 
 * SPDH tagy podle SmartShop Verze1 (UPS)
 * 
 * @author Tomas Jacko <tomas.jacko [at] monetplus.cz>
 *
 */
public enum UpcV1SpdhTag implements SpdhTag {
	
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
	 * POSConditionCode. (e)
	 */
	POSConditionCode("e"),
	/**
	 * Autorizacni kod. (F)
	 */
	ApprovalCode("F"),
	/**
	 * Text k zobrazeni na terminalu. (g) (48 znaku)
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
	 * Pouziva se pro vraceni expirace karty ve formatu YYMMDD
	 */
	BusinessDate("K"),
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
	 * Cislo originalni uctenky. (S)
	 */
	OriginalReceiptCode("S"),
	/**
	 * LanguageTable. - tabulka pro kodovani jazyka (U)
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
	 * Cas vzniku offline transakce ve formaty yyMMddHHmmss. (6A)
	 */
	OfflineTxTime("6A"),
	/**
	 * Ciselny kod meny. (6I)
	 */
	CurrencyCode("6I"),
	/**
	 * POSEntryMode. (6E)
	 */
	POSEntryMode("6E"),
	/**
	 * Customer info(100B) text zobrazuje se na uctence, jednotlivé řádky jsou zalomeny CR,LF. (9h)
	 */
	CustomerInfo("9h"),
	/**
	 * Customer Phone. (9N)
	 */
	CustomerPhone("9N"),
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
	 */
	ReceiptCreation("94"),
	/**
	 * Identifikacni kod prodavajiciho. (95)
	 */
	SellerCode("95"),
	/**
	 * Identifikacni kod darku. (96)
	 */
	PresentCode("96"),
	/**
	 * Hlavicka komoditni transakce. Struktura: X(8) [XID - identifikator platby] + X(4) [celkovy pocet polozek v
	 * platbe] (9H)
	 */
	CommodityHeader("9H"),
	/**
	 * Segment komoditni transakce. Struktura: X(4) [index prvni polozky v dane transakci] + X(4) [pocet polozek v dane
	 * transakci] (9S)
	 */
	CommoditySegment("9S"),
	/**
	 * Data komoditni transakce. Big endian binarni struktura zakodovana v BASE64. Struktura binarnich dat:
	 * 2B(productGroup) + 1B(VAT group) + 4B(amount * 1000) + 4B(unit price * 1000) + 4B(total price * 100) (9D)
	 */
	CommodityData("9D"),
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
	 * eVoucher cislo (9v)
	 */
	EVoucher("9v"),
	/**
	 * EAN13
	 */
	EAN13("9a"),
	/**
	 * cca 20 mistne cislo
	 */
	Code128("9b"),

	/**
	 * Tabulka prevodnich kurzu 
	 */
	ProjectExchange("9X"),
	/**
	 * Total amount 
	 */
	TotalAmount("9B"),
	/**
	 * SummaryInfo 
	 */
	SummaryInfo("9I"),
	/**
	 * Track 2 karty obsluhy (pokladni) 
	 */
	CashierTrack2("9q"),
	/**
	 * Unikatni cislo jizdy (9s)
	 */
	CommoditySegmentTT("9s"),
	/**
	 * Tarif (9t)
	 */
	Tarif("9t"),
	/**
	 * Komercni text (9g) - max 100 B
	 */
	CommercialText("9g"),
	/**
	 * Zpusob dobiti, 1B
	 * 0 - in cash
	 * 1 - by card
	 * 2 - by food stamp
	 */
	ReplenishmentType("D"),

	/**
	 * Payment type
	 * 0 - Taxamenter
	 * 1 - Driver
	 */
	PaymentType("9p")
	;

	;

	private final String tag;
	
	private UpcV1SpdhTag(String tag) {
		this.tag = tag;
	}

	@Override
	public String getTag() {
		return tag;
	}

}
