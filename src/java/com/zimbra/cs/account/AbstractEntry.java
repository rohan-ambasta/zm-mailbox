/*
 * ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1
 * 
 * The contents of this file are subject to the Mozilla Public License
 * Version 1.1 ("License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.zimbra.com/license
 * 
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
 * the License for the specific language governing rights and limitations
 * under the License.
 * 
 * The Original Code is: Zimbra Collaboration Suite Server.
 * 
 * The Initial Developer of the Original Code is Zimbra, Inc.
 * Portions created by Zimbra are Copyright (C) 2006 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * Contributor(s): 
 * 
 * ***** END LICENSE BLOCK *****
 */

package com.zimbra.cs.account;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.zimbra.cs.account.Entry;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.service.ServiceException;
import com.zimbra.cs.util.DateUtil;

public abstract class AbstractEntry implements Entry {

    private Map<String,Object> mAttrs;
    private Map<String,Object> mDefaults;    
    private Map<Object, Object> mData;
    private Map<String, Set<String>> mMultiAttrSetCache;

    protected static String[] sEmptyMulti = new String[0];

    private AbstractEntry(Map<String,Object> attrs) {
        mAttrs = attrs;
    }

    protected AbstractEntry(Map<String,Object> attrs, Map<String,Object> defaults) {
        this(attrs);
        mDefaults = defaults;
    }

    protected void setAttrs(Map<String,Object> attrs, Map<String,Object> defaults) {
        mAttrs = attrs;
        mDefaults = defaults;
        resetData();
    }
    
    protected void setAttrs(Map<String,Object> attrs) {
        mAttrs = attrs;
        resetData();
    }
    
    public void setDefaults(Map<String,Object> defaults) {
        mDefaults = defaults;
        resetData();
    }
    
    protected void resetData()
    {
        if (mMultiAttrSetCache != null)            
            mMultiAttrSetCache.clear();
        if (mData != null)
            mData.clear();
    }

    /**
     * looks up name in map, and if found, returns its value.
     * if not found, iterate through key names and compare using equalsIgnoreCase
     * @param name
     * @return
     */
    private Object getObject(String name) {
        Object v = mAttrs.get(name);
        if (v != null) return v;
        
        for (String key: mAttrs.keySet()) {
            if (key.equalsIgnoreCase(name))
                return mAttrs.get(key);
        }
        
        if (mDefaults == null) return null;
        
        v = mDefaults.get(name);
        if (v != null) return v;
        
        for (String key: mDefaults.keySet()) {
            if (key.equalsIgnoreCase(name))
                return mDefaults.get(key);
        }
        return null;
    }
    
    public String getAttr(String name) {
        Object v = getObject(name);
        if (v instanceof String) {
            return (String) v;
        } else if (v instanceof String[]) {
            String[] a = (String[]) v;
            return a.length > 0 ? a[0] : null;
        } else {
            return null;
        }
    }

    public String getAttr(String name, String defaultValue) {
        String v = getAttr(name);
        return v == null ? defaultValue : v;
    }

    public Map<String, Object> getAttrs() throws ServiceException {
        return getAttrs(true);
    }

    public Map<String, Object> getAttrs(boolean applyDefaults) throws ServiceException {
        if (applyDefaults && mDefaults != null) {
            Map<String, Object> attrs = new HashMap<String, Object>();
            // put the defaults
            attrs.putAll(mDefaults);
            // override with currently set
            attrs.putAll(mAttrs);
            return attrs;
        } else {
            return mAttrs;
        }
    }

    public boolean getBooleanAttr(String name, boolean defaultValue) {
        String v = getAttr(name);
        return v == null ? defaultValue : Provisioning.TRUE.equals(v);
    }

    public synchronized Object getCachedData(Object key) {
        if (mData == null)
            mData = new HashMap<Object, Object>();
        return mData.get(key);
    }

    public Date getGeneralizedTimeAttr(String name, Date defaultValue) {
        String v = getAttr(name);
        if (v == null)
            return defaultValue;
        Date d = DateUtil.parseGeneralizedTime(v);
        return d == null ? defaultValue : d;
    }

    public int getIntAttr(String name, int defaultValue) {
        String v = getAttr(name);
        try {
            return v == null ? defaultValue : Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public Locale getLocale() throws ServiceException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("not implemented");
    }

    public long getLongAttr(String name, long defaultValue) {
        String v = getAttr(name);
        try {
            return v == null ? defaultValue : Long.parseLong(v);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public String[] getMultiAttr(String name) {
        Object v = getObject(name);
        if (v instanceof String) return new String[] {(String) v};
        else if (v instanceof String[]) {
            return (String[])v;
        } else {
            return sEmptyMulti;
        }
    }

    public Set<String> getMultiAttrSet(String name) {
        if (mMultiAttrSetCache == null)        
            mMultiAttrSetCache = new HashMap<String, Set<String>>();        
        Set<String> result = mMultiAttrSetCache.get(name);
        if (result == null) {
            result = new HashSet<String>(Arrays.asList(getMultiAttr(name)));
            mMultiAttrSetCache.put(name, result);
        }
        return result;
    }

    public long getTimeInterval(String name, long defaultValue) {
        return DateUtil.getTimeInterval(getAttr(name), defaultValue);        
    }

    public synchronized void setCachedData(Object key, Object value) {
        if (mData == null)
            mData = new HashMap<Object, Object>();
        mData.put(key, value);
    }
    
    protected void getDefaults(AttributeFlag flag, Map<String,Object> defaults) throws ServiceException {
        defaults.clear();
        Set<String> attrs = AttributeManager.getInstance().getAttrsWithFlag(flag);
        for (String a : attrs) {
            Object obj = getObject(a);
            if (obj != null) defaults.put(a, obj);
        }
        //return Collections.unmodifiableMap(defaults);
    }

    public synchronized String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getName()).append(": {  ");        
        //sb.append(getClass().getName()).append(": { name=").append(getName()).append(" ");
        sb.append(mAttrs.toString());
        sb.append("}");
        return sb.toString();           
    }
}
