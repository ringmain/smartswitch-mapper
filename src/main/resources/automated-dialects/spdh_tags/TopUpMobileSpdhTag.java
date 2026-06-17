package cz.monetplus.smartswitch.spdh.tags;

import cz.monetplus.spdh.commons.SpdhTag;

/**
 * 
 * SPDH tagy podle Top Up Mobile Verze 1 (dobijeni)
 * 
 *
 */
public enum TopUpMobileSpdhTag implements SpdhTag {

	/**
	 * Typ Operatora (4a)
	 */
	MobileOperator("4a"),

	/**
	 * Telefonni cislo. (4b)
	 */
	TelNo("4b"),

	/**
	 * Notifikacni cislo. (4c)
	 */
	NotNo("4c"),

	/**
	 * EAN kod. (4d)
	 */
	Ean("4d"),

	/**
	 * Nazev providera (4e)
	 */
	ProviderName("4e"),

	/**
	 * GSM Topup data (4f)
	 */
	GsmTopupData("4f"),

	;

	private final String tag;

	TopUpMobileSpdhTag(String tag) {
		this.tag = tag;
	}

	@Override
	public String getTag() {
		return tag;
	}

}
