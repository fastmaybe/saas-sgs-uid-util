package com.sf.saas.uid;

import com.sf.saas.uid.exception.UidGenerateException;

/**
 * @author 01407460
 * @date 2022/5/20 17:16
 */
public interface UidGenerator {


    /**
     * Get a unique ID
     *
     * @return UID
     * @throws UidGenerateException
     */
    long getUID() throws UidGenerateException;

    /**
     * Parse the UID into elements which are used to generate the UID. <br>
     * Such as timestamp & workerId & sequence...
     *
     * @param uid
     * @return Parsed info
     */
    String parseUID(long uid);
}
