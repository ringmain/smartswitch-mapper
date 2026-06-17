package cz.monetplus.smartswitch.spdh.tags;

import cz.monetplus.spdh.commons.SpdhTag;

/**
 * 
 * SPDH tagy podle SmartShop V1
 * 
 * @author Kristyna Nemeckova <kristyna.nemeckova [at] monetplus.cz>
 *
 */
public enum SShopV1SpdhTag implements SpdhTag {

	/**
	 * Replenishment method - zpusob dobiti (D)
	 */
	ReplenishmentMethod("D"),

	;

	private final String tag;

	SShopV1SpdhTag(String tag) {
		this.tag = tag;
	}

	@Override
	public String getTag() {
		return tag;
	}

}
