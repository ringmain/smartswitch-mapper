package cz.monetplus.smartswitch.spdh.tags;

import cz.monetplus.spdh.commons.SpdhTag;

/**
 * 
 * SPDH tagy pro Monet-bank-MOL aplikaci
 * 
 *
 */
public enum PosMolV1SpdhTag implements SpdhTag {

	/**
	 * 4g - text - 1-40 (X?) - ASCII
	 */
	ReceiptText("4g"),

	/**
	 * Card num (4q) 13-19 (N)
	 */
	LoyaltyNumber("4q"),

	/**
	 * Points (4B) 1-12 (N)
	 */
	Points("4B"),

	/**
	 * Balance. (4J) - 1-12 (-N)
	 */
	PointBalance("4J"),

	/**
	 * Basket (4Y) - 5 (X?)
	 */
	BasketId("4Y"),

	/**
	 * Merit response code (4X) - 2(X?)
	 */
	MeritRC("4X"),

	/**
	 *  Processor Id (9N) - 2
	 */
	ProcessorId("9N"),

	;

	private final String tag;

	PosMolV1SpdhTag(String tag) {
		this.tag = tag;
	}

	@Override
	public String getTag() {
		return tag;
	}

}
