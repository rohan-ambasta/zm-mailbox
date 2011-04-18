/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2010, 2011 Zimbra, Inc.
 *
 * The contents of this file are subject to the Zimbra Public License
 * Version 1.3 ("License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the License at
 * http://www.zimbra.com/license.
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied.
 * ***** END LICENSE BLOCK *****
 */
package com.zimbra.cs.index.query;

import org.apache.lucene.analysis.Analyzer;

import com.zimbra.cs.index.DBQueryOperation;
import com.zimbra.cs.index.LuceneFields;
import com.zimbra.cs.index.QueryOperation;
import com.zimbra.cs.mailbox.Mailbox;

/**
 * Query by sender.
 *
 * @author tim
 * @author ysasaki
 */
public final class SenderQuery extends Query {
    private String sender;
    private boolean lt;
    private boolean inclusive;

    /**
     * This is only used for subject queries that start with {@code <} or {@code >}, otherwise we just use the normal
     * {@link TextQuery}.
     */
    private SenderQuery(String text) {
        lt = (text.charAt(0) == '<');
        inclusive = false;
        sender = text.substring(1);

        if (sender.charAt(0) == '=') {
            inclusive = true;
            sender = sender.substring(1);
        }
    }

    public static Query create(Analyzer analyzer, String text) {
        if (text.length() > 1 && (text.startsWith("<") || text.startsWith(">"))) {
            return new SenderQuery(text);
        } else {
            return new TextQuery(analyzer, LuceneFields.L_H_FROM, text);
        }
    }

    @Override
    public boolean hasTextOperation() {
        return false;
    }

    @Override
    public QueryOperation compile(Mailbox mbox, boolean bool) {
        DBQueryOperation op = new DBQueryOperation();
        if (lt) {
            op.addSenderRange(null, false, sender, lt, evalBool(bool));
        } else {
            op.addSenderRange(sender, inclusive, null, false, evalBool(bool));
        }
        return op;
    }

    @Override
    public void dump(StringBuilder out) {
        out.append("SENDER:");
        out.append(lt ? '<' : '>');
        if (inclusive) {
            out.append('=');
        }
        out.append(sender);
    }

}
