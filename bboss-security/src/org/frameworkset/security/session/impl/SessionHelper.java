/*
 *  Copyright 2008 bbossgroups
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.frameworkset.security.session.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Pattern;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.frameworkset.nosql.mongodb.MongoDBHelper;
import org.frameworkset.security.session.InvalidateCallback;
import org.frameworkset.security.session.MongoDBUtil;
import org.frameworkset.security.session.Session;
import org.frameworkset.security.session.SessionBasicInfo;
import org.frameworkset.security.session.domain.CrossDomain;
import org.frameworkset.security.session.statics.AttributeInfo;
import org.frameworkset.security.session.statics.NullSessionStaticManagerImpl;
import org.frameworkset.security.session.statics.SessionConfig;
import org.frameworkset.security.session.statics.SessionStaticManager;
import org.frameworkset.spi.BaseApplicationContext;
import org.frameworkset.spi.DefaultApplicationContext;

import com.frameworkset.util.SimpleStringUtil;
import com.frameworkset.util.StringUtil;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

/**
 * <p>Title: SessionHelper.java</p> 
 * <p>Description: </p>
 * <p>bboss workgroup</p>
 * <p>Copyright (c) 2008</p>
 * @Date 2014年4月30日
 * @author biaoping.yin
 * @version 3.8.0
 */
public class SessionHelper {
	private static SessionManager sessionManager;
	private static SessionStaticManager sessionStaticManager;
	private static boolean inited = false;
	public static SessionConfig getSessionConfig(String appcode)
	{
		return sessionManager.getSessionConfig(appcode,true);
	}
	public static SessionConfig getSessionConfig(String appcode,boolean serialattributes)
	{
		return sessionManager.getSessionConfig(appcode, serialattributes);
	}
	
	public static boolean filter(String key) {
		return key.equals("maxInactiveInterval") || key.equals("creationTime")
				|| key.equals("lastAccessedTime") || key.equals("referip")
				|| key.equals("_validate") || key.equals("sessionid")
				|| key.equals("_id") || key.equals("appKey")
				|| key.equals("host")
				|| key.equals("secure")
				|| key.equals("httpOnly")
				|| key.equals("requesturi")
				|| key.equals("lastAccessedUrl")
				|| key.equals("lastAccessedHostIP");
			
	}
	
	public static void removeSession(String sessionId,HttpServletRequest request)
	{
		if(request instanceof SessionHttpServletRequestWrapper)
		{
			((SessionHttpServletRequestWrapper)request).removeSession(sessionId);
		}
	}
	
	public static void removeSession(String sessionId,String appcode)
	{
		if( SessionHelper.getSessionManager().usewebsession())
			return;
		
		HttpSession session = _getSession(appcode, sessionId);
		if(session != null)
			session.invalidate();
	
	}
	private static HttpSession _getSession(String appkey,String sessionid) {
		if( SessionHelper.getSessionManager().usewebsession())
		{
			return null;
		}
		HttpSessionImpl session = null;
		if(sessionid == null)
		{
			
			return session;
		}
		
		else
		{
//			String appkey =  SessionHelper.getAppKey(this);

			Session session_ = SessionHelper.getSession(appkey,null,sessionid);
			if(session_ == null)//session不存在，创建新的session
			{				
				return null;
			}
			else
			{
				session =  new HttpSessionImpl(session_,null,null,null);
			}
			return session;
		}
		
		
	}
	public static void init(String contextpath){
		if(inited)
			return ;
		synchronized(SessionHelper.class)
		{
			if(inited)
				return ;
			try
			{
				BaseApplicationContext context = DefaultApplicationContext.getApplicationContext("sessionconf.xml");
				SessionManager sessionManager = context.getTBeanObject("sessionManager", SessionManager.class);
				SessionStaticManager sessionStaticManager = null;
				String monitorScope = null;
				if(!sessionManager.usewebsession())
				{
					sessionStaticManager = context.getTBeanObject("sessionStaticManager", SessionStaticManager.class);
					monitorScope = sessionStaticManager.getMonitorScope();
				}
				else
					sessionStaticManager = new NullSessionStaticManagerImpl();
				if(monitorScope == null)
					monitorScope = SessionStaticManager.MONITOR_SCOPE_SELF;
				sessionManager.initSessionConfig(contextpath,monitorScope);
				SessionHelper.sessionManager = sessionManager;
				SessionHelper.sessionStaticManager = sessionStaticManager;
			}
			finally
			{
				inited = true;
			}
		}
		
	}
	
	public static Object convertValue(String value,AttributeInfo attributeInfo)
	{
		if(attributeInfo.getType().equals("String"))
			return value;
		else if(attributeInfo.getType().equals("int"))
			return Integer.parseInt(value);
		else if(attributeInfo.getType().equals("long"))
			return Long.parseLong(value);
		else if(attributeInfo.getType().equals("double"))
			return Double.parseDouble(value);
		else if(attributeInfo.getType().equals("float"))
			return Float.parseFloat(value);
		else if(attributeInfo.getType().equals("boolean"))
			return Boolean.parseBoolean(value);
		return value;
	}
	
	public static Map<String,AttributeInfo> parserExtendAttributes(HttpServletRequest request,SessionConfig sessionConfig  )
	{
		AttributeInfo[] monitorAttributeArray = sessionConfig.getExtendAttributeInfos();
		if(monitorAttributeArray == null || monitorAttributeArray.length == 0)
		{
			return null;
		}
		Map<String,AttributeInfo>  datas = new HashMap<String,AttributeInfo>();
		for(AttributeInfo attributeInfo : monitorAttributeArray)
		{
			String value = request.getParameter(attributeInfo.getName());
			if(value != null )
			{
				
				if(value.trim().equals("")  )
				{
					if(attributeInfo.isEnableEmptyValue())
					{
						String enableEmptyValue = request.getParameter(attributeInfo.getName()+"_enableEmptyValue");
						if(enableEmptyValue !=  null)
						{
							try {
								attributeInfo = attributeInfo.clone();
								attributeInfo.setValue("");
								datas.put(attributeInfo.getName(), attributeInfo);
							} catch (CloneNotSupportedException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
					}
					
				}
				else
				{
					try {
						attributeInfo = attributeInfo.clone();
						attributeInfo.setValue(SessionHelper. convertValue(  value,  attributeInfo));
						datas.put(attributeInfo.getName(), attributeInfo);
					} catch (CloneNotSupportedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				
			}
		}
		return datas;
	}
	
	public static void evalqueryfields(AttributeInfo[] monitorAttributeArray, Map keys)
	{
		 
		if(monitorAttributeArray != null && monitorAttributeArray.length > 0)
		{
			for(AttributeInfo attr:monitorAttributeArray)
			{
				keys.put(attr.getName(), 1);
				 
				
			}
			
			
		}
		
		
		
	}
	
	public static void buildExtendFieldQueryCondition(Map<String, AttributeInfo> monitorAttributeArray,  BasicDBObject query,String serialType)
	{
		 
		if(monitorAttributeArray != null && monitorAttributeArray.size() > 0)
		{
			
			for(Entry<String, AttributeInfo> Entry:monitorAttributeArray.entrySet())
			{
				AttributeInfo  attr = Entry.getValue();
				if(attr.getType().equals("String"))
				{
					
					if(!attr.isLike())
					{
						if (!StringUtil.isEmpty((String)attr.getValue())) {
							Object value = serial(attr.getValue(),serialType);
							query.append(attr.getName(), value);
						}
						else if(attr.isEnableEmptyValue())
						{
							BasicDBList values = new BasicDBList();
							values.add(new BasicDBObject(attr.getName(), serial("",serialType)));
							values.add(new BasicDBObject(attr.getName(), null));
							query.append("$or", values);
						}
						

						
					}
					else
					{
						if (!StringUtil.isEmpty((String)attr.getValue())) {
							Object value = attr.getValue();
							//getLikeCondition(String condition,String serialType)
//							Pattern hosts = Pattern.compile("^<ps><p n=\"_dflt_\" s:t=\"String\"><\\!\\[CDATA\\[" + value + ".*$",
//									Pattern.CASE_INSENSITIVE);
							Pattern hosts = Pattern.compile(getLikeCondition(value,  serialType),
									Pattern.CASE_INSENSITIVE);
							query.append(attr.getName(), new BasicDBObject("$regex",hosts));
						}
						else if(attr.isEnableEmptyValue())
						{
							 
							//values.add(null);
						
							BasicDBList values = new BasicDBList();
							values.add(new BasicDBObject(attr.getName(), serial("",serialType)));
							values.add(new BasicDBObject(attr.getName(), null));
							query.append("$or", values);
							
							
						}
					}
				}
				else 
				{
					Object value = serial(attr.getValue(),serialType);
					query.append(attr.getName(), value);
				}
				
			}
			
			
		}
		
		
		
	}
	public static List<AttributeInfo> evalqueryfiledsValue(AttributeInfo[] monitorAttributeArray, DBObject dbobject,String serialType)  
	{
		List<AttributeInfo> extendAttrs = null;
		 
		if(monitorAttributeArray != null && monitorAttributeArray.length > 0)				
		{
			extendAttrs = new ArrayList<AttributeInfo>();
			AttributeInfo attrvalue = null;
			for(AttributeInfo attributeInfo:monitorAttributeArray)
			{
				try {
					attrvalue = attributeInfo.clone();
					String value = (String)dbobject.get(attrvalue.getName());
					attrvalue.setValue(unserial(  value,serialType));
					extendAttrs.add(attrvalue);
				} catch (CloneNotSupportedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
			}
		}
		return extendAttrs;
		
	}
	public static void destroy() {
		sessionManager = null;
		
	}


	public static SessionManager getSessionManager() {
		return sessionManager;
	}
	
	public static SessionStaticManager getSessionStaticManager()
	{
		return sessionStaticManager;
	}
	
	public static HttpSession createSession(ServletContext servletContext,SessionBasicInfo sessionBasicInfo,String contextpath,InvalidateCallback invalidateCallback)
	{
		HttpSession session = sessionManager.getSessionStore().createHttpSession(   servletContext,  sessionBasicInfo,  contextpath,  invalidateCallback);
		
		return session;
	}
	public static void dispatchEvent(SessionEventImpl sessionEvent) 
	{
		sessionManager.dispatchEvent(sessionEvent);
	}
	
	public static boolean haveSessionListener() 
	{
		return sessionManager.haveSessionListener();
	}

	public static Session getSession(String appkey,String contextPath, String sessionid) {
		// TODO Auto-generated method stub
		return sessionManager.getSessionStore().getSession(appkey,contextPath, sessionid);
	}
	

	public static String serial(Object value)
	{
//		if(value != null)
//		{
//			try {
//				value = ObjectSerializable.toXML(value);
////				value = new String(((String)value).getBytes(Charset.defaultCharset()),"UTF-8");
//			} catch (Exception e) {
//				throw new RuntimeException(e);
//			}
//		}
//		return value;
		return sessionManager.getSessionSerial().serialize(value);
	}
	
	public static Object serial(Object value,String serialType)
	{
//		if(value != null)
//		{
//			try {
//				value = ObjectSerializable.toXML(value);
////				value = new String(((String)value).getBytes(Charset.defaultCharset()),"UTF-8");
//			} catch (Exception e) {
//				throw new RuntimeException(e);
//			}
//		}
//		return value;
		return sessionManager.getSessionSerial(serialType).serialize(value);
	}
	
	public static String getLikeCondition(Object condition,String serialType)
	{
		return sessionManager.getSessionSerial(serialType).handleLikeCondition(condition);
	}
	
	public static Object unserial(String value)
	{
		return sessionManager.getSessionSerial().deserialize(value);
	}
	public static Object unserial(String value,String serialType)
	{
		return sessionManager.getSessionSerial( serialType).deserialize(value);
	}
	public static String wraperAttributeName(String appkey,String contextpath, String attribute)
	{
		CrossDomain crossDomain = sessionManager.getCrossDomain();
		if(crossDomain == null)
			return attribute;
		return crossDomain.wraperAttributeName(appkey, contextpath, attribute);
	}
	
	public static String dewraperAttributeName(String appkey,String contextpath, String attribute)
	{
		CrossDomain crossDomain = sessionManager.getCrossDomain();
		if(crossDomain == null)
			return attribute;
		return crossDomain.dewraperAttributeName(appkey, contextpath, attribute);
	}
	public static String getAppKey(HttpServletRequest request)
	{
		String appcode = getSessionManager().getAppcode();
		if(appcode != null)
		{
			return appcode;
		}
		 return getAppKeyFromRequest(request);
		
	}
	
	public static String getAppKeyFromRequest(HttpServletRequest request)
	{
//		String appcode = getSessionManager().getAppcode();
//		if(appcode != null)
//		{
//			return appcode;
//		}
		
		if(request != null)
		{
			String appKey = request.getContextPath().replace("/", "");
			if(appKey.equals(""))
				appKey = "ROOT";
			return appKey;
		}
		return null;
		
	}
	
	public static String getAppKeyFromServletContext(ServletContext context)
	{
//		String appcode = getSessionManager().getAppcode();
//		if(appcode != null)
//		{
//			return appcode;
//		}
		
		if(context != null)
		{
			
			String appKey = context.getContextPath().replace("/", "");
			if(appKey.equals(""))
				appKey = "ROOT";
			return appKey;
		}
		return null;
		
	}
	
	public static boolean hasMonitorPermission(String app, HttpServletRequest request)
	{
		return getSessionStaticManager().hasMonitorPermission(app, request);
	}
	
	public static boolean hasDeleteAppPermission(String app, HttpServletRequest request)
	{
		return getSessionStaticManager().hasMonitorPermission(app, request);
	}
	
	public static boolean deleteApp(String app) throws Exception
	{
		return getSessionStaticManager().deleteApp(app);
	}
	
	public static boolean isMonitorAll() throws Exception
	{
		return getSessionStaticManager().isMonitorAll();
	}
	/**
	 * 
	 * @param monitorAttributes
	 * @return
	 */
	public static  AttributeInfo[] getExtendAttributeInfos(String monitorAttributes)
	{
		
		 if(StringUtil.isEmpty(monitorAttributes))
			 return null;
		AttributeInfo[] monitorAttributeArray = SimpleStringUtil.json2Object(monitorAttributes,AttributeInfo[].class);
//		 AttributeInfo[] monitorAttributeArray = null;
//		if(!StringUtil.isEmpty(monitorAttributes))
//		{
//			String[] monitorAttributeArray_ = monitorAttributes.split(",");
//			monitorAttributeArray = new AttributeInfo[monitorAttributeArray_.length];
//			AttributeInfo attributeInfo = null;
//			for(int i = 0; i < monitorAttributeArray_.length; i ++)
//			{
//				String attr = monitorAttributeArray_[i];
//				attributeInfo = new AttributeInfo();
//				String attrinfo[] = attr.split(":");
//				if(attrinfo.length > 2)
//				{
//					attributeInfo.setName(attrinfo[0]);
//					attributeInfo.setType(attrinfo[1]);
//					attributeInfo.setCname(attrinfo[2]);
//				}
//				else if(attrinfo.length > 1)
//				{
//					attributeInfo.setName(attrinfo[0]);
//					attributeInfo.setType(attrinfo[1]);
//				}
//				else
//				{
//					attributeInfo.setName(attrinfo[0]);
//					attributeInfo.setType("String");
//				}
//				monitorAttributeArray[i]=attributeInfo;
//					
//				
//			}
//			
//		}
		return monitorAttributeArray;
	}
	public static List<AttributeInfo> evalqueryfiledsValue(AttributeInfo[] attributeInfos, List<String> data, int offset,String serialType) {
		List<AttributeInfo> extendAttrs = null;
		 
		if(attributeInfos != null && attributeInfos.length > 0)				
		{
			extendAttrs = new ArrayList<AttributeInfo>();
			AttributeInfo attrvalue = null;
			for(int j = 0; j < attributeInfos.length; j ++ )
			{
				AttributeInfo attributeInfo = attributeInfos[j];
				try {
					attrvalue = attributeInfo.clone();
					String value = data.get(j +offset);
					attrvalue.setValue(unserial(  value,serialType));
					extendAttrs.add(attrvalue);
				} catch (CloneNotSupportedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
			}
		}
		return extendAttrs;
	}
	
	public static Map<String,Object> toMap(DBObject object,boolean deserial) {

		Set set = object.keySet();
		if (set != null && set.size() > 0) {
			Map<String,Object> attrs = new HashMap<String,Object>();
			Iterator it = set.iterator();
			while (it.hasNext()) {
				String key = (String) it.next();
				if (!MongoDBUtil.filter(key)) {
					Object value = object.get(key);
					try {
						attrs.put(MongoDBHelper.recoverSpecialChar(key),
								deserial?SessionHelper.unserial((String) value):value);
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
			return attrs;
		}
		return null;
	}
	
	public static Map<String,Object> toMap(String appkey,String contextpath,DBObject object,boolean deserial) {

		Set set = object.keySet();
		if (set != null && set.size() > 0) {
			Map<String,Object> attrs = new HashMap<String,Object>();
			Iterator it = set.iterator();
			while (it.hasNext()) {
				String key = (String) it.next();
				if (!MongoDBUtil.filter(key)) {
					Object value = object.get(key);
					try {
						String temp = MongoDBHelper.recoverSpecialChar(key);
						temp = SessionHelper.dewraperAttributeName(appkey, contextpath, temp);
						if(temp != null)
							attrs.put(temp,
									deserial?SessionHelper.unserial((String) value):value);
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
			return attrs;
		}
		return null;
	}
}
