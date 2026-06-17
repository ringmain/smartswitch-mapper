package cz.monetplus.smartswitch.spdh.tags;

import cz.monetplus.spdh.commons.SpdhTag;

/**
 * 
 * SPDH tagy podle MOS Verze 1 (registrace)
 * 
 *
 */
public enum MosV1SpdhTag implements SpdhTag {
	
	/**
	 * Typ karty - litacka, bezkontaktni platebni karta. (4a)
	 */
	CardType("4a"),

	/**
	 * Identifikator karty. (4b)
	 */
	Uuid("4b"),

	/**
	 * FormFactor. (4c)
	 */
	FormFactor("4c"),

	/**
	 * Expirace ve formatu yyMMdd. (4d)
	 */
	LongExpiry("4d"),

	/**
	 * Tokeny (9d)
	 */
	Tokens("9d"),

	;

	private final String tag;
	
	private MosV1SpdhTag(String tag) {
		this.tag = tag;
	}

	@Override
	public String getTag() {
		return tag;
	}

}
