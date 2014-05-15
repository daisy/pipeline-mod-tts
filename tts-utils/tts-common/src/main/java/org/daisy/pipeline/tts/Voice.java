package org.daisy.pipeline.tts;

public class Voice {
	public Voice(String vendor, String name) {
		this.vendor = vendor;
		if (vendor == null)
			this.vendor = "";
		this.name = name;
		if (name == null)
			this.name = "";

		mVendor_lo = this.vendor.toLowerCase();
		mName_lo = this.name.toLowerCase();
	}

	public int hashCode() {
		return mVendor_lo.hashCode() ^ mName_lo.hashCode();
	}

	public boolean equals(Object other) {
		if (other == null)
			return false;
		Voice v2 = (Voice) other;
		return mVendor_lo.equals(v2.mVendor_lo) && mName_lo.equals(v2.mName_lo);
	}

	public String toString() {
		return "{vendor:" + (!vendor.isEmpty() ? vendor : "%unknown%") + ", name:"
		        + (!name.isEmpty() ? name : "%unkown%") + "}";
	}

	//the upper-case versions need to be kept because some TTS Processors like SAPI
	//are case-sensitive. Lower-case versions are only used for comparison.
	public String vendor;
	public String name;
	private String mVendor_lo;
	private String mName_lo;
}
