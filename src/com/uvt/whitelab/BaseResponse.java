/**
 * Copyright (c) 2013, 2014 Tilburg University.
 * All rights reserved.
 *
 * @author MvdCamp
 */
package com.uvt.whitelab;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MalformedObjectNameException;
import javax.management.ReflectionException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.json.JSONException;
import org.json.JSONObject;

import com.uvt.whitelab.util.MetadataField;
import com.uvt.whitelab.util.QueryServiceHandler;

/**
 *
 */
public abstract class BaseResponse {

	private final String OUTPUT_ENCODING = "UTF-8";

	protected HttpServletRequest request;
	protected HttpServletResponse response;
	protected WhiteLab servlet;
	private List<String> requiredParameters;
	private VelocityContext context = new VelocityContext();
	protected ResourceBundle labels;
	protected Locale locale;
	protected String lastUrl = null;
	protected Map<String,Object> params;
	protected String lang;
	
	protected long startTime = new Date().getTime();

	protected BaseResponse() {
		requiredParameters = new ArrayList<String>();
	}
	
	public WhiteLab getServlet() {
		return this.servlet;
	}

	/**
	 * Initialise this object with
	 * @param argRequest
	 * @param argResponse
	 * @param argServlet
	 */
	public void init(HttpServletRequest argRequest, HttpServletResponse argResponse, WhiteLab argServlet) {
		request = argRequest;
		response = argResponse;
		servlet = argServlet;
	}

	/**
	 * Get the velocity context object
	 *
	 * @return velocity context
	 */
	protected VelocityContext getContext() {
		return context;
	}

	protected void clearContext() {
		context = new VelocityContext();
	}

	protected Map<String, Object> getQueryParameters() throws UnsupportedEncodingException {
		Map<String, Object> params = new HashMap<String,Object>();
		String query = this.getParameter("query", "");
		query = query.replaceAll("&", "%26");
		
		int view = this.getParameter("view", 1);
		
		if (query.length() > 0) {
			try {

				params.put("patt", query);
				
				String groupBy = this.getParameter("groupBy", "");
				if (groupBy.length() > 0)
					params.put("group", URLDecoder.decode(groupBy, "UTF-8"));
				
				String sort = this.getParameter("sort", "");
				if (sort.length() > 0) {
					this.servlet.log("SORTING BY "+sort);
					params.put("sort", URLDecoder.decode(sort, "UTF-8"));
				}
				
				Integer start = this.getParameter("start", -1);
				if (start > -1)
					params.put("start", start);
				
				Integer end = this.getParameter("end", -1);
				if (end > -1)
					params.put("end", end);
				
				Integer first = this.getParameter("first", 0);
				if (first > 0)
					params.put("first", first);
				
				Integer number = this.getParameter("number", 50);
				String docPid = this.getParameter("docpid", "");
				if (docPid.length() == 0)
					params.put("number", number);
				
				String filter = getFilterString();
				if (filter.length() > 0) {
					params.put("filter", filter);
				}
				
				if (view == 12)
					params.put("wordsaroundhit", 0);
				
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
		}
		
		return params;
	}
	
	private String getFilterString() {
		Map<String,Map<String,List<String>>> filters = new HashMap<String,Map<String,List<String>>>();
		
		for (MetadataField dataField : this.servlet.getMetadataFields()) {
			String[] filterValues = this.getParameterValues(dataField.getName(), null);
			if (filterValues != null && filterValues.length > 0) {
				Map<String,List<String>> vals = new HashMap<String,List<String>>();
				List<String> is = new ArrayList<String>();
				List<String> isnot = new ArrayList<String>();
				vals.put("is", is);
				vals.put("isnot", isnot);
				
				for (int i = 0; i < filterValues.length; i++) {
					String filterValue = "";
					try {
						filterValue = URLDecoder.decode(filterValues[i], "UTF-8");
					} catch (UnsupportedEncodingException e) {
						e.printStackTrace();
					}
					
					if (filterValue.startsWith("-") || filterValue.startsWith("\"-")) {
						filterValue = filterValue.replaceFirst("-", "");
						vals.get("isnot").add(filterValue);
					} else {
						vals.get("is").add(filterValue);
					}
				}
				
				if (vals.get("isnot").size() > 0 && vals.get("is").size() == 0) {
					for (String filterValue : dataField.getValues()) {
						filterValue = "\""+filterValue+"\"";
						if (!vals.get("isnot").contains(filterValue) && !vals.get("is").contains(filterValue))
							vals.get("is").add(filterValue);
					}
				} else {
					vals.remove("isnot");
				}
				
				filters.put(dataField.getName(), vals);
			}
		}

		List<String> filterStrings = new ArrayList<String>();
		String filter = "";
		if (filters.keySet().size() > 0) {
			for (String field : filters.keySet()) {
				filterStrings.add(field+":("+StringUtils.join(filters.get(field).get("is").toArray(), " OR ").replaceAll("&", "%26")+")");
			}
			filter = "("+StringUtils.join(filterStrings.toArray()," AND ")+")";
		}
		return filter;
	}
	
	protected String getBlackLabResponse(String corpus, String trail, Map<String,Object> params) {
		String url = this.labels.getString("baseUrl")+"/"+corpus+trail;
		this.lastUrl = url;
		String parameters = getParameterStringExcept(new String[]{});
		
		if (parameters.length() > 0) {
			url = url + "?" + parameters;
		}
		
		QueryServiceHandler webservice = new QueryServiceHandler(url, 1);
		try {
			String response = webservice.makeRequest(new HashMap<String, String[]>());
			return response;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
 
	protected String getParameterStringExcept(String[] except) {
		String parameters = "";
		
		if (params != null && params.keySet().size() > 0) {
			for (String key : params.keySet()) {
				if (!Arrays.asList(except).contains(key)) {
					if (parameters.length() > 0)
						parameters = parameters + "&" + key + "=" + params.get(key);
					else
						parameters = key + "=" +params.get(key);
				}
			}
		}
		
		parameters = parameters.replaceAll(" ", "%20");
		return parameters;
	}

//	private static Document convertStringToDocument(String xmlStr) {
//        try {  
//        	DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();  
//            DocumentBuilder builder = factory.newDocumentBuilder();  
//            Document doc = builder.parse( new InputSource( new StringReader( xmlStr ) ) ); 
//            return doc;
//        } catch (Exception e) {  
//            e.printStackTrace();  
//        } 
//        return null;
//    }

	/**
	 * Display a specific template, with specific mime type
	 *
	 * @param argT
	 * @param mime
	 */
	protected void displayTemplate(Template argT, String mime) {
		// Set the content headers for the response
		response.setCharacterEncoding(OUTPUT_ENCODING);
		response.setContentType(mime);

		// Merge context into the page template and write to output stream
		try {
			OutputStreamWriter osw = new OutputStreamWriter(response.getOutputStream(), OUTPUT_ENCODING);
			try {
				argT.merge(getContext(), osw);
				osw.flush();
			} finally {
				osw.close();
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	protected String applyTemplate(Template argT) {
		StringWriter writer = new StringWriter();
        argT.merge( getContext(), writer );
        return writer.toString();
	}
	
	protected String applyHtmlTemplate(Template argT) {
		return applyTemplate(argT);
	}

	/**
	 * Display a template with the XML mime type
	 * @param argT
	 */
	protected void displayHtmlTemplate(Template argT) {
		displayTemplate(argT, "text/html");
	}

	/**
	 * Calls the completeRequest and logRequest implementations
	 */
	final public void processRequest() {
		try {
			this.servlet.log("("+this.getClass()+", patt="+this.getParameter("query", "")+") Start memory usage: "+this.servlet.getCurrentMemUsage());
		} catch (MalformedObjectNameException | AttributeNotFoundException | InstanceNotFoundException | MBeanException | ReflectionException e) {
			e.printStackTrace();
		}
		this.locale = request.getLocale();
		this.lang = this.request.getParameter("lang");
		
		if (this.lang != null && !this.lang.equals(this.locale)) {
			this.locale = new Locale(this.lang);
		} else if (this.lang == null) {
			this.lang = this.locale.getLanguage();
		}
		this.labels = ResourceBundle.getBundle("WhitelabBundle", this.locale);
		
		this.getContext().put("lang", this.lang);
		this.getContext().put("labels", this.labels);
		
		// if we have enough parameters to complete this request...
		if (sufficientParameters()) {
			logRequest();
			
			try {
				this.params = getQueryParameters();
				if (this.params.keySet().size() > 0)
					this.servlet.log("Query parameters given: "+StringUtils.join(params.keySet().toArray(),", "));
			} catch (UnsupportedEncodingException e1) {
				e1.printStackTrace();
			}
			
			completeRequest();
		} else {
			// insufficient parameters supplied, return error
			this.getContext().put("error", "Insufficient parameters");
			this.displayHtmlTemplate(this.servlet.getTemplate("error"));
		}
	}

	/**
	 * Add a required parameter to the list
	 * @param param
	 */
	protected void addRequiredParameter(String param) {
		requiredParameters.add(param);
	}

	/**
	 * Check if all parameters necessary to complete a search request exist
	 * @return true/false
	 */
	private boolean sufficientParameters() {
		// for each parameter in the list
		for(String p : requiredParameters) {
			// if it is missing, return false
			if(request.getParameter(p) == null) {
				System.out.println("Missing parameter: "+p);
				return false;
			}

			// if, after trimming, it is empty, return false
			if(request.getParameter(p).trim().length() < 1) {
				System.out.println("Missing parameter: "+p);
				return false;
			}
		}

		// everything is accounted for, return true!
		return true;
	}

	/**
	 * Returns the value of a servlet parameter, or the default value
	 *
	 * @param name
	 *            name of the parameter
	 * @param defaultValue
	 *            default value
	 * @return value of the paramater
	 */
	public String getParameter(String name, String defaultValue) {
		// get the trimmed parameter value
		String value = request.getParameter(name);

		if(value != null) {
			value = value.trim();

			// if the parameter value is an empty string
			if (value.length() == 0)
				value = defaultValue;
		} else {
			value = defaultValue;
		}

		return value;
	}

	/**
	 * Returns the value of a servlet parameter, or the default value
	 *
	 * @param name
	 *            name of the parameter
	 * @param defaultValue
	 *            default value
	 * @return value of the paramater
	 */
	public int getParameter(String name, int defaultValue) {
		final String stringToParse = getParameter(name, "" + defaultValue);
		try {
			return Integer.parseInt(stringToParse);
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	public String[] getParameterValues(String name, String defaultValue) {
		String[] values = this.request.getParameterValues(name);

		if(values == null)
//			logger.debug("No values found for label "+name);
			values = new String[]{};

		return values;
	}

	public List<String> getParameterValuesAsList(String name, String defaultValue) {
		return Arrays.asList(getParameterValues(name, defaultValue));
	}

	public Integer getParameter(String name, Integer defaultValue) {
		final String stringToParse = getParameter(name, "" + defaultValue);

		return new Integer(stringToParse);
	}

	/**
	 * Returns the value of a servlet parameter, or the default value
	 *
	 * @param name
	 *            name of the parameter
	 * @param defaultValue
	 *            default value
	 * @return value of the paramater
	 */
	public boolean getParameter(String name, boolean defaultValue) {
		return getParameter(name, defaultValue ? "on" : "").equals("on");
	}

	protected String loadStylesheet(String name) throws IOException {
		// clear string builder
		StringBuilder builder = new StringBuilder();
		builder.delete(0, builder.length());

		BufferedReader br = new BufferedReader(new FileReader(this.servlet.getRealPath() + "WEB-INF/stylesheets/" + name ));

		String line;
		
		this.servlet.log("Loading stylesheet: "+name);

		// read the response from the webservice
		while( (line = br.readLine()) != null )
			builder.append(line);

		br.close();

		return builder.toString();
	}

	protected void loadMetaDataComponents() {
		Map<String,String> options = new HashMap<String,String>();
		Map<String,String> selectFields = new HashMap<String,String>();
		SortedSet<String> filters = new TreeSet<String>();
		Map<String,String> filterIds = new HashMap<String,String>();
			
		for (MetadataField dataField : this.servlet.getMetadataFields()) {
			
			//Get display name for field
			String fieldName = dataField.getName();
			if (this.labels.containsKey("metadataFields."+fieldName))
				fieldName = this.labels.getString("metadataFields."+fieldName);
			
			filters.add(fieldName);
			filterIds.put(fieldName, dataField.getName());
			
			//Generate option HTML
			String option = "<option value=\"field:"+dataField.getName()+"\">"+fieldName+"</option>";
			options.put(fieldName, option);
			
			if (dataField.numberOfValues() > 0) {
				
				List<String> vals = new ArrayList<String>();
				Map<String,String> fdOptions = new HashMap<String,String>();
				for (String value : dataField.getValues()) {
					vals.add(value);
					fdOptions.put(value, "<option value=\""+value+"\">"+value+"</option>");
				}

				String select = "<select class=\"metaInput\"><option value=\"\" selected></option>";
				SortedSet<String> keys = new TreeSet<String>(vals);
				for (String fieldValue : keys) {
					select = select+fdOptions.get(fieldValue);
				}
				if (!dataField.isComplete())
					select = select+"<option value=\"other\">"+this.labels.getString("other")+"</option>";
				
				select = select+"</select>";
				selectFields.put(dataField.getName(), select);
			} else {
				String select = "<input class=\"metaInput\" type=\"text\" />";
				selectFields.put(dataField.getName(), select);
			}
		}
		
		String rule = "<div class=\"rule row large-16 medium-16 small-16\">"
			+ "<div class=\"large-4 medium-4 small-4 columns\">"
			+ "<select class=\"metaLabel\">"
			+ "<option value=\"\" disabled=\"true\" selected=\"true\"></option>";
		
		Iterator<String> it = filters.iterator();
		while (it.hasNext()) {
			rule = rule + options.get(it.next());
		}
			
		rule = rule + "</select>"
			+ "</div>"
			+ "<div class=\"large-3 medium-3 small-3 columns\">"
			+ "<select class=\"metaOperator\">"
			+ "<option value=\"is\" selected=\"true\">"+this.labels.getString("meta.is")+"</option>"
			+ "<option value=\"not\">"+this.labels.getString("meta.not")+"</option>"
			+ "</select>"
			+ "</div>"
			+ "<div class=\"large-7 medium-7 small-7 columns\">"
			+ "<input class=\"metaInput\" type=\"text\">"
			+ "</div>"
			+ "<div class=\"large-2 medium-2 small-2 columns\">"
			+ "<a class=\"meta-min\" onclick=\"Whitelab.meta.removeRule(this)\">"
			+ "<img src=\"../web/img/minus.png\">"
			+ "</a>"
			+ "<a class=\"meta-plus\" onclick=\"Whitelab.meta.addRule()\">"
			+ "<img src=\"../web/img/plus.png\">"
			+ "</a>"
			+ "</div>"
			+ "</div>";

		this.getContext().put("metaRule", rule);
		this.getContext().put("filters", filters);
		this.getContext().put("filterIds", filterIds);
		this.getContext().put("metaOptions",options);
		this.getContext().put("metaSelect",selectFields);
	}

	protected void sendResponse(Map<String,Object> output) {
		long timePassed = new Date().getTime() - this.startTime;
		try {
			this.servlet.log("("+this.getClass()+", patt="+this.getParameter("query", "")+") End memory usage: "+this.servlet.getCurrentMemUsage()+", execution time: "+timePassed);
		} catch (MalformedObjectNameException | AttributeNotFoundException | InstanceNotFoundException | MBeanException | ReflectionException e) {
			e.printStackTrace();
		}
		JSONObject resp = new JSONObject();
		try {
			for (String key : output.keySet()) {
				resp.put(key, output.get(key));
			}
			response.setHeader("Access-Control-Allow-Origin", "*");
	        response.setHeader("Access-Control-Allow-Methods", "POST");
	        response.setHeader("Access-Control-Allow-Headers", "Content-Type");
	        response.setHeader("Access-Control-Max-Age", "3600");
			response.setContentType("application/x-javascript; charset=utf-8");
			response.getWriter().write(resp.toString());
			response.getWriter().close();
		} catch (JSONException | IOException e) {
			e.printStackTrace();
		}
	}
	
	protected void sendCsvResponse(String csv) {
        response.setHeader("Access-Control-Allow-Methods", "GET");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");
        response.setHeader("Access-Control-Max-Age", "3600");
	    response.setHeader("Content-Type", "text/csv");
	    response.setHeader("Content-Disposition", "attachment;filename=\"file.csv\"");
	    
	    BufferedWriter writer;
		try {
			writer = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), "UTF-8"));
		    writer.append(csv);
		    writer.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Complete the request - automatically called by processRequest()
	 */
	abstract protected void completeRequest();

	/**
	 * Log the request - automatically called by processRequest()
	 */
	abstract protected void logRequest();

	abstract public BaseResponse duplicate();

}