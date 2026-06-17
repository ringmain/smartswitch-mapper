package cz.monetplus.smartswitch.spdh.tags;

import cz.monetplus.spdh.commons.SpdhTag;

/**
 * 
 * SPDH tagy podle SmartShop Verze1 (FESTIVAL)
 * 
 * @author knemeckova
 *
 */
public enum FestV1SpdhTag implements SpdhTag {
	
	/**
	 * Action code. (4a)
	 */
	ActionCode("4a"),
	/**
	 * Balance. (4b)
	 */
	Balance("4b"),
	/**
	 * Counter. (4c)
	 */
	Counter("4c"),
	/**
	 * Last cmd. (4d)
	 */
	LastCmd("4d"),
	/**
	 * Last amount. (4e)
	 */
	LastAmount("4e"),
	/**
	 * Last ctx. (4f)
	 */
	CreditCtx("4f"),
	/**
	 * Pin count (4g)
	 */
	PinCount("4g"),
	/**
	 * Pin limit (4h)
	 */
	PinLimit("4h"),
	
	/**
	 * RNDB (4i)
	 */
	RNDB("4i"),

	/**
	 * Podezreni na fraud (4j)
	 */
	IsFraud("4j"),

	
	/**
	 * Vybiti (4z)
	 */
	Discharge("4z"),

	/**
	 * Cislo zakaznicke karty
	 */
	CustomerPan("4q"),
	
	/**
	 * SAM number (5q)
	 */
	SamPan("5q"),
	/**
	 * SAM Action code. (5a)
	 */
	SamActionCode("5a"),
	/**
	 * SAM Balance. (5b)
	 */
	SamBalance("5b"),
	/**
	 * SAM Counter. (5c)
	 */
	SamCounter("5c"),
	/**
	 * SAM Last cmd. (5d)
	 */
	SamLastCmd("5d"),
	/**
	 * SAM Last amount. (5e)
	 */
	SamLastAmount("5e"),
	/**
	 * SAM Last ctx. (5f)
	 */
	SamCreditCtx("5f"),
	/**
	 * SAM Pin count (5g)
	 */
	SamPinCount("5g"),
	/**
	 * SAM Pin limit (5h)
	 */
	SamPinLimit("5h"),
	/**
	 * SAM RNDB (5i)
	 */
	SamRNDB("5i"),
	
	;

	private final String tag;
	
	private FestV1SpdhTag(String tag) {
		this.tag = tag;
	}

	@Override
	public String getTag() {
		return tag;
	}

}
