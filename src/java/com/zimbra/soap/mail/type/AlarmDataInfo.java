/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2011 Zimbra, Inc.
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

package com.zimbra.soap.mail.type;

import com.google.common.base.Objects;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;

import com.zimbra.common.soap.MailConstants;

@XmlAccessorType(XmlAccessType.FIELD)
public class AlarmDataInfo {

    @XmlAttribute(name=MailConstants.A_CAL_NEXT_ALARM, required=false)
    private Long nextAlarm;

    @XmlAttribute(name=MailConstants.A_CAL_ALARM_INSTANCE_START, required=false)
    private Long alarmInstanceStart;

    @XmlAttribute(name=MailConstants.A_CAL_INV_ID, required=true)
    private final Integer invId;

    @XmlAttribute(name=MailConstants.A_CAL_COMPONENT_NUM, required=true)
    private final Integer componentNum;

    @XmlAttribute(name=MailConstants.A_NAME, required=false)
    private String name;

    @XmlAttribute(name=MailConstants.A_CAL_LOCATION, required=false)
    private String location;

    @XmlElement(name=MailConstants.E_CAL_ALARM, required=false)
    private AlarmInfo alarm;

    /**
     * no-argument constructor wanted by JAXB
     */
    @SuppressWarnings("unused")
    private AlarmDataInfo() {
        this((Integer) null, (Integer) null);
    }

    public AlarmDataInfo(Integer invId, Integer componentNum) {
        this.invId = invId;
        this.componentNum = componentNum;
    }

    public void setNextAlarm(Long nextAlarm) { this.nextAlarm = nextAlarm; }
    public void setAlarmInstanceStart(Long alarmInstanceStart) {
        this.alarmInstanceStart = alarmInstanceStart;
    }
    public void setName(String name) { this.name = name; }
    public void setLocation(String location) { this.location = location; }
    public void setAlarm(AlarmInfo alarm) { this.alarm = alarm; }
    public Long getNextAlarm() { return nextAlarm; }
    public Long getAlarmInstanceStart() { return alarmInstanceStart; }
    public Integer getInvId() { return invId; }
    public Integer getComponentNum() { return componentNum; }
    public String getName() { return name; }
    public String getLocation() { return location; }
    public AlarmInfo getAlarm() { return alarm; }

    public Objects.ToStringHelper addToStringInfo(
                Objects.ToStringHelper helper) {
        return helper
            .add("nextAlarm", nextAlarm)
            .add("alarmInstanceStart", alarmInstanceStart)
            .add("invId", invId)
            .add("componentNum", componentNum)
            .add("name", name)
            .add("location", location)
            .add("alarm", alarm);
    }

    @Override
    public String toString() {
        return addToStringInfo(Objects.toStringHelper(this))
                .toString();
    }
}
