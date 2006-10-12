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
 * Portions created by Zimbra are Copyright (C) 2005, 2006 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * Contributor(s): 
 * 
 * ***** END LICENSE BLOCK *****
 */

/*
 * Created on May 26, 2004
 */
package com.zimbra.cs.service.mail;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.WellKnownTimeZone;
import com.zimbra.cs.index.*;
import com.zimbra.cs.index.MailboxIndex.SortBy;
import com.zimbra.cs.index.SearchParams.ExpandResults;
import com.zimbra.cs.mailbox.Appointment;
import com.zimbra.cs.mailbox.Conversation;
import com.zimbra.cs.mailbox.Flag;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.MailItem;
import com.zimbra.cs.mailbox.Message;
import com.zimbra.cs.mailbox.WikiItem;
import com.zimbra.cs.operation.ItemActionOperation;
import com.zimbra.cs.operation.SearchOperation;
import com.zimbra.cs.operation.Operation.Requester;
import com.zimbra.cs.service.ServiceException;
import com.zimbra.cs.service.util.ItemId;
import com.zimbra.cs.session.PendingModifications;
import com.zimbra.cs.session.SessionCache;
import com.zimbra.cs.session.SoapSession;
import com.zimbra.soap.Element;
import com.zimbra.soap.ZimbraSoapContext;


public class Search extends MailDocumentHandler  {
    protected static Log mLog = LogFactory.getLog(Search.class);

    public Element handle(Element request, Map<String, Object> context) throws ServiceException {
        ZimbraSoapContext zsc = getZimbraSoapContext(context);
        SoapSession session = (SoapSession) zsc.getSession(SessionCache.SESSION_SOAP);
        Mailbox mbox = getRequestedMailbox(zsc);
        SearchParams params = parseCommonParameters(request, zsc);

        SearchOperation op = new SearchOperation(session, zsc, zsc.getOperationContext(), mbox, Requester.SOAP,  params);
        op.schedule();
        ZimbraQueryResults results = op.getResults();

        try {
            // create the XML response Element
            Element response = zsc.createElement(MailService.SEARCH_RESPONSE);

            // must use results.getSortBy() because the results might have ignored our sortBy
            // request and used something else...
            SortBy sb = results.getSortBy();
            response.addAttribute(MailService.A_SORTBY, sb.toString());

            ResultsPager pager = ResultsPager.create(results, params);
            response.addAttribute(MailService.A_QUERY_OFFSET, params.getOffset());

            Element retVal = putHits(zsc, response, pager, DONT_INCLUDE_MAILBOX_INFO, params);

            return retVal;
        } finally {
            results.doneWithSearchResults();
        }
    }
    
    protected static final String LOCALE_PATTERN = "([a-zA-Z]{2})[-_]([a-zA-Z]{2})([-_](.+))?";
    protected final static Pattern sLocalePattern = Pattern.compile(LOCALE_PATTERN);
    
    protected Locale parseLocale(Element localeElt, ZimbraSoapContext zsc) {
        String locStr = localeElt.getText();
        
        if (locStr != null && locStr.length() > 0) {
            Matcher m = sLocalePattern.matcher(locStr);
            if (m.lookingAt()) {
                String lang=null, country=null, variant=null;
                
                if (m.start(1) != -1)
                    lang = locStr.substring(m.start(1), m.end(1));
                
                if (lang == null || lang.length()<=0)
                    return null;
                
                if (m.start(2) != -1)
                    country = locStr.substring(m.start(2), m.end(2));
                
                if (m.start(4) != -1)
                    variant = locStr.substring(m.start(4), m.end(4));
                
                if (variant != null && country != null && variant.length() > 0 && country.length() > 0)
                    return new Locale(lang, country, variant);
                
                if (country != null && country.length() > 0)
                    return new Locale(lang, country);
                
                return new Locale(lang);
            }
        }
        
        
        return null;
    }
    
    protected java.util.TimeZone parseTimeZonePart(Element tzElt, ZimbraSoapContext zsc) throws ServiceException {
        String id = tzElt.getAttribute(MailService.A_ID);

        // is it a well-known timezone?  if so then we're done here
        WellKnownTimeZone knownTZ = Provisioning.getInstance().getTimeZoneById(id);
        if (knownTZ != null)
            return knownTZ.toTimeZone();

        // custom timezone!
        
        String test = tzElt.getAttribute(MailService.A_APPT_TZ_STDOFFSET, null);
        if (test == null)
            throw ServiceException.INVALID_REQUEST("Unknown TZ: \""+id+"\" and no "+MailService.A_APPT_TZ_STDOFFSET+" specified", null);
        
        return CalendarUtils.parseTzElement(tzElt);
    }

    protected SearchParams parseCommonParameters(Element request, ZimbraSoapContext zsc) throws ServiceException {
        String query = request.getAttribute(MailService.E_QUERY, null);
        if (query == null)
            query = getRequestedAccount(zsc).getAttr(Provisioning.A_zimbraPrefMailInitialSearch);

        if (query == null)
            throw ServiceException.INVALID_REQUEST("no query submitted and no default query found", null);

        SearchParams params = new SearchParams();
        
        //
        // <loc>
        Element locElt = request.getOptionalElement(MailService.E_LOCALE);
        if (locElt != null) {
            Locale loc = parseLocale(locElt, zsc);
            params.setLocale(loc);
        }

        //
        // <tz>
        Element tzElt = request.getOptionalElement(MailService.E_APPT_TZ);
        if (tzElt != null) {
            TimeZone tz = parseTimeZonePart(tzElt, zsc);
            params.setTimeZone(tz);
        }
        
        params.setOffset(getOffset(request));
        params.setLimit(getLimit(request));
        params.setQueryStr(query);
        String groupByStr = request.getAttribute(MailService.A_SEARCH_TYPES, null);
        if (groupByStr == null)
            groupByStr = request.getAttribute(MailService.A_GROUPBY, MailboxIndex.GROUP_BY_CONVERSATION);
        params.setTypesStr(groupByStr);
        params.setSortByStr(request.getAttribute(MailService.A_SORTBY, MailboxIndex.SortBy.DATE_DESCENDING.toString()));
        params.setFetchFirst(ExpandResults.get(request.getAttribute(MailService.A_FETCH, null)));
        if (params.getFetchFirst() != ExpandResults.NONE) {
            params.setWantHtml(request.getAttributeBool(MailService.A_WANT_HTML, false));
            params.setMarkRead(request.getAttributeBool(MailService.A_MARK_READ, false));
        }
        params.setWantRecipients(request.getAttributeBool(MailService.A_RECIPIENTS, false));

        Element cursor = request.getOptionalElement(MailService.E_CURSOR);
        if (cursor != null) {
//            int prevMailItemId = (int)cursor.getAttributeLong(MailService.A_ID);
//          int prevOffset = (int)cursor.getAttributeLong(MailService.A_QUERY_OFFSET);
            
            String cursorStr = cursor.getAttribute(MailService.A_ID);
            ItemId prevMailItemId = null;
            if (cursorStr != null)
                prevMailItemId = new ItemId(cursorStr, zsc);
            
            int prevOffset = 0;
            String sortVal = cursor.getAttribute(MailService.A_SORTVAL);
            params.setCursor(prevMailItemId, sortVal, prevOffset);
        }

        return params;
    }

    protected int getLimit(Element request) throws ServiceException {
        int limit = (int) request.getAttributeLong(MailService.A_QUERY_LIMIT, -1);
        if (limit <= 0 || limit > 1000)
            limit = 30; // default limit of...say..30...
        return limit;
    }

    protected int getOffset(Element request) throws ServiceException {
        // Lookup the offset= and limit= parameters in the soap request
        return (int) request.getAttributeLong(MailService.A_QUERY_OFFSET, 0);
    }

    // just to make the argument lists a bit more readable:
    protected final boolean INCLUDE_MAILBOX_INFO = true;
    protected final boolean DONT_INCLUDE_MAILBOX_INFO = false;

    protected Element putHits(ZimbraSoapContext zsc, Element response, ResultsPager pager, boolean includeMailbox, SearchParams params)
    throws ServiceException {
        int offset = params.getOffset();
        int limit  = params.getLimit();
        EmailElementCache eecache = new EmailElementCache();

        if (mLog.isDebugEnabled())
            mLog.debug("Search results beginning with offset " + offset);

        int totalNumHits = 0;
        ExpandResults expand = params.getFetchFirst();
        if (expand == ExpandResults.HITS)
            expand = ExpandResults.NONE;      // "hits" is not a valid value for SearchConv...
        for (ZimbraHit hit : pager.getHits()) {
//          for (ZimbraHit hit = results.skipToHit(offset); hit != null; hit = results.getNext()) {
            if (++totalNumHits > limit) {
                if (mLog.isDebugEnabled()) {
                    mLog.debug("Search results limited to " + limit + " hits.");
                }
                break;
            }
            boolean inline = (totalNumHits == 1 && expand == ExpandResults.FIRST) || expand == ExpandResults.ALL;
            boolean addSortField = true;

            Element e = null;
            if (hit instanceof ConversationHit) {
                ConversationHit ch = (ConversationHit) hit;
                e = addConversationHit(zsc, response, ch, eecache, params);
            } else if (hit instanceof MessageHit) {
                MessageHit mh = (MessageHit) hit;
                e = addMessageHit(zsc, response, mh, eecache, inline, params);
            } else if (hit instanceof MessagePartHit) {
                MessagePartHit mph = (MessagePartHit) hit;
                e = addMessagePartHit(response, mph, eecache);                
            } else if (hit instanceof ContactHit) {
                ContactHit ch = (ContactHit) hit;
                e = ToXML.encodeContact(response, zsc, ch.getContact(), null, !inline, null);
            } else if (hit instanceof NoteHit) {
                NoteHit nh = (NoteHit) hit;
                e = ToXML.encodeNote(response,zsc, nh.getNote());
            } else if (hit instanceof ProxiedHit) {
                ProxiedHit ph = (ProxiedHit) hit;
                response.addElement(ph.getElement().detach());
                addSortField = false;
            } else if (hit instanceof AppointmentHit) {
                AppointmentHit ah = (AppointmentHit)hit;
                e = addAppointmentHit(zsc, response, ah, inline, params);
            } else if (hit instanceof DocumentHit) {
                DocumentHit dh = (DocumentHit)hit;
                e = addDocumentHit(zsc, response, dh);
            } else {
                mLog.error("Got an unknown hit type putting search hits: "+hit);
                continue;
            }
            if (e != null && addSortField) {
                e.addAttribute(MailService.A_SORT_FIELD, hit.getSortField(pager.getSortOrder()).toString());
            }
            if (e != null && includeMailbox) {
//              String idStr = hit.getMailboxIdStr() + "/" + hit.getItemId();
                ItemId iid = new ItemId(hit.getAcctIdStr(), hit.getItemId());
                e.addAttribute(MailService.A_ID, iid.toString());
            }
        }

        response.addAttribute(MailService.A_QUERY_MORE, pager.hasNext());

        return response;
    }


    protected Element addConversationHit(ZimbraSoapContext zsc, Element response, ConversationHit ch, EmailElementCache eecache, SearchParams params)
    throws ServiceException {
        Conversation conv = ch.getConversation();
        MessageHit mh = ch.getFirstMessageHit();
        Element c = ToXML.encodeConversationSummary(response, zsc, conv, mh == null ? null : mh.getMessage(), eecache, params.getWantRecipients());
        if (ch.getScore() != 0)
            c.addAttribute(MailService.A_SCORE, ch.getScore());

        Collection s = ch.getMessageHits();
        if (s != null) {
            for (Iterator mit = s.iterator(); mit.hasNext(); ) {
                mh = (MessageHit) mit.next();
                Message msg = mh.getMessage();
                Element e = c.addElement(MailService.E_MSG);
                e.addAttribute(MailService.A_ID, msg.getId());
            }
        }
        return c;
    }

    protected Element addAppointmentHit(ZimbraSoapContext zsc, Element response, AppointmentHit ah, boolean inline, SearchParams params)
    throws ServiceException {
        Appointment appt = ah.getAppointment();
        Element m;
        if (inline) {
            m = ToXML.encodeApptSummary(response, zsc, appt, PendingModifications.Change.ALL_FIELDS);
//          if (!msg.getFragment().equals(""))
//          m.addAttribute(MailService.E_FRAG, msg.getFragment(), Element.DISP_CONTENT);
        } else {
            m = ToXML.encodeApptSummary(response, zsc, appt, PendingModifications.Change.ALL_FIELDS);
        }

        if (ah.getScore() != 0) {
            m.addAttribute(MailService.A_SCORE, ah.getScore());
        }

        m.addAttribute(MailService.A_CONTENTMATCHED, true);

        return m;
    }


    protected Element addMessageHit(ZimbraSoapContext zsc, Element response, MessageHit mh, EmailElementCache eecache, boolean inline, SearchParams params)
    throws ServiceException {
        Message msg = mh.getMessage();

        // for bug 7568, mark-as-read must happen before the response is encoded.
        if (inline && msg.isUnread() && params.getMarkRead()) {
            // Mark the message as READ          
            try {
                ArrayList<Integer> ids = new ArrayList<Integer>(1);
                ids.add(msg.getId());
                ItemActionOperation.TAG(zsc, null, zsc.getOperationContext(), msg.getMailbox(), Requester.SOAP, ids, MailItem.TYPE_MESSAGE, false, null, Flag.ID_FLAG_UNREAD);
            } catch (ServiceException e) {
                mLog.warn("problem marking message as read (ignored): " + msg.getId(), e);
            }
        }
        
        Element m;
        if (inline) {
            m = ToXML.encodeMessageAsMP(response, zsc, msg, null, params.getWantHtml(), true);
            if (!msg.getFragment().equals(""))
                m.addAttribute(MailService.E_FRAG, msg.getFragment(), Element.DISP_CONTENT);
        } else
            m = ToXML.encodeMessageSummary(response, zsc, msg, params.getWantRecipients());
        if (mh.getScore() != 0)
            m.addAttribute(MailService.A_SCORE, mh.getScore());

        m.addAttribute(MailService.A_CONTENTMATCHED, true);

        List<MessagePartHit> parts = mh.getMatchedMimePartNames();
        if (parts != null) {
            for (MessagePartHit mph : parts) {
                String partNameStr = mph.getPartName();

                if (partNameStr.length() > 0) {
                    Element mp = m.addElement(MailService.E_HIT_MIMEPART);
                    mp.addAttribute(MailService.A_PART, partNameStr);
                }
            }
        }
        
        return m;
    }

    protected Element addMessageHit(ZimbraSoapContext zsc, Element response, Message msg, EmailElementCache eecache, SearchParams params) {
        Element m = ToXML.encodeMessageSummary(response, zsc, msg, params.getWantRecipients());
        return m;
    }

    protected Element addMessagePartHit(Element response, MessagePartHit mph, EmailElementCache eecache) throws ServiceException {
        MessageHit mh = mph.getMessageResult();
        Message msg = mh.getMessage();
        Element mp = response.addElement(MailService.E_MIMEPART);

        mp.addAttribute(MailService.A_SIZE, msg.getSize());
        mp.addAttribute(MailService.A_DATE, msg.getDate());
        mp.addAttribute(MailService.A_CONV_ID, msg.getConversationId());
        mp.addAttribute(MailService.A_MESSAGE_ID, msg.getId());
        mp.addAttribute(MailService.A_CONTENT_TYPE, mph.getType());
        mp.addAttribute(MailService.A_CONTENT_NAME, mph.getFilename());
        mp.addAttribute(MailService.A_PART, mph.getPartName());        
        if (mph.getScore() != 0)
            mp.addAttribute(MailService.A_SCORE, mph.getScore());

        eecache.makeEmail(mp, msg.getSender(), EmailElementCache.EMAIL_TYPE_FROM, null);
        String subject = mph.getSubject();
        if (subject != null)
            mp.addAttribute(MailService.E_SUBJECT, subject, Element.DISP_CONTENT);

        return mp;
    }

    Element addContactHit(ZimbraSoapContext zsc, Element response, ContactHit ch, EmailElementCache eecache) throws ServiceException {
        return ToXML.encodeContact(response, zsc, ch.getContact(), null, true, null);
    }

    Element addDocumentHit(ZimbraSoapContext zsc, Element response, DocumentHit dh) throws ServiceException {
        int ver = dh.getVersion();
        if (dh.getItemType() == MailItem.TYPE_DOCUMENT)
            return ToXML.encodeDocument(response, zsc, dh.getDocument(), ver);
        else if (dh.getItemType() == MailItem.TYPE_WIKI)
            return ToXML.encodeWiki(response, zsc, (WikiItem)dh.getDocument(), ver);
        throw ServiceException.UNKNOWN_DOCUMENT("invalid document type "+dh.getItemType(), null);
    }

    Element addNoteHit(ZimbraSoapContext zsc, Element response, NoteHit nh, EmailElementCache eecache) throws ServiceException {
        // TODO - does this need to be a summary, instead of the whole note?
        return ToXML.encodeNote(response, zsc, nh.getNote());
    }
}
