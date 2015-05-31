package com.uvt.whitelab.response.search;

import com.uvt.whitelab.BaseResponse;

public class SimpleResponse extends BaseResponse {

	@Override
	protected void completeRequest() {
		this.displayHtmlTemplate(this.servlet.getTemplate("search/simple"));
	}

	@Override
	protected void logRequest() {
		this.servlet.log("SimpleResponse");
	}

	@Override
	public SimpleResponse duplicate() {
		return new SimpleResponse();
	}

}