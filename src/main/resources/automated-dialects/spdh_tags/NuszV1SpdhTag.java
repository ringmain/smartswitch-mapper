package cz.monetplus.smartswitch.spdh.tags;

import cz.monetplus.spdh.commons.SpdhTag;

/**
 * 
 * SPDH tagy pro NUSZ (E-Vignette, Toll)
 * 
 * @author knemeckova
 *
 */
public enum NuszV1SpdhTag implements SpdhTag {

	// Both E-Vignette, Toll
	ValidFrom("4A"),
	ValidUntil("4B"),
	ViUniqueId("4C"),
	UniqueId("4D"),

	// E-Vignette 4,
	Type("4a"),
	Category("4b"),
	RegPlate("4c"),
	Year("4d"),
	CountryCode("4e"),
	ViId("4f"),

	ProviderName("4z"),

	//Toll 5
	TopupType("5a"),
	Category_toll("5b"),
	RegNum("5c"),
	TopupDate("5d"),
	AccountNo("5e"),
	AccountBalance("5f"),
	TopupId("5g"),
	Price("5h"),
	Itiner("5i"),
	HgvBan("5j"),
	Height("5k"),
	Weight("5l"),
	Length("5m"),
	Width("5n"),
	Axleload("5o"),
	TticketId("5p"),

	;

	private final String tag;

	private NuszV1SpdhTag(String tag) {
		this.tag = tag;
	}

	@Override
	public String getTag() {
		return tag;
	}

}
