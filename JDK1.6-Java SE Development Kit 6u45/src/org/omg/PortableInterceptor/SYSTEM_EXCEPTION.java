package org.omg.PortableInterceptor;


/**
* org/omg/PortableInterceptor/SYSTEM_EXCEPTION.java .
* Generated by the IDL-to-Java compiler (portable), version "3.2"
* from ../../../../src/share/classes/org/omg/PortableInterceptor/Interceptors.idl
* Tuesday, March 26, 2013 2:15:02 PM GMT-08:00
*/

public interface SYSTEM_EXCEPTION
{

  /**
     * Indicates a SystemException reply status. One possible value for 
     * <code>RequestInfo.reply_status</code>.
     * @see RequestInfo#reply_status
     * @see SUCCESSFUL
     * @see USER_EXCEPTION
     * @see LOCATION_FORWARD
     * @see TRANSPORT_RETRY
     */
  public static final short value = (short)(1);
}