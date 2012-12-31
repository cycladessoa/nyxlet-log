/*******************************************************************************
 * Copyright (c) 2012, THE BOARD OF TRUSTEES OF THE LELAND STANFORD JUNIOR UNIVERSITY
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 *    Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *    Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *    Neither the name of the STANFORD UNIVERSITY nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 *******************************************************************************/
package org.cyclades.nyxlet.log.actionhandler;

import org.cyclades.engine.exception.CycladesException;
import org.cyclades.engine.logging.LogWriter;
import org.cyclades.engine.nyxlet.templates.stroma.STROMANyxlet;
import org.cyclades.engine.NyxletSession;
import org.cyclades.engine.ResponseCodeEnum;
import org.cyclades.engine.nyxlet.templates.stroma.actionhandler.ChainableActionHandler;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.cyclades.annotations.AHandler;
import org.cyclades.engine.stroma.STROMAResponse;
import org.cyclades.engine.stroma.STROMAResponseWriter;
import org.cyclades.engine.validator.ParameterHasValue;
import org.json.JSONArray;
import org.json.JSONObject;

@AHandler({"appendlog"})
public class LogAppender extends ChainableActionHandler {

    public LogAppender (STROMANyxlet parentNyxlet) {
        super(parentNyxlet);
    }

    @Override
    public void init () throws Exception {
        JSONArray logWriterArray = new JSONArray(getParentNyxlet().getExternalProperties().getPropertyOrError(LOG_WRITERS_PROPERTY));
        JSONObject logWriterJSONObject;
        LogWriterDelegate logWriterDelegate;
        for (int i = 0; i < logWriterArray.length(); i++) {
            logWriterJSONObject = logWriterArray.getJSONObject(i);
            logWriterDelegate = new LogWriterDelegate(
                    new LogWriter(getParentNyxlet().getEngineContext().getCanonicalEngineDirectoryPath(logWriterJSONObject.getString("path")),
                            logWriterJSONObject.getString("name"),
                            logWriterJSONObject.getString("date_pattern")
                    ),
                    logWriterJSONObject.has("entry-date-stamp") && logWriterJSONObject.getString("entry-date-stamp").equalsIgnoreCase("true")
            );
            if (logWriterJSONObject.has("delimeter")) logWriterDelegate.setDelimeter(logWriterJSONObject.getString("delimeter"));
            logWriters.put(logWriterJSONObject.getString("alias"), logWriterDelegate);
        }
        this.getFieldValidators().add(new ParameterHasValue(LOG_ALIAS_PARAMETER));
    }

    @Override
    public void destroy () throws Exception {
        try {
            for (Map.Entry<String, LogWriterDelegate> logEntry : logWriters.entrySet()) {
                try { logEntry.getValue().getLogWriter().close(); } catch (Exception e) {}
            }
        } catch (Exception e) {
            getParentNyxlet().logStackTrace(e);
        }
    }

    @Override
    public void handleMapChannel (NyxletSession nyxletSession, Map<String, List<String>> baseParameters, STROMAResponseWriter stromaResponseWriter) throws Exception {
        handleLocal(nyxletSession, baseParameters, stromaResponseWriter, (String)nyxletSession.getMapChannelObject(INPUT_MAP_CHANNEL_KEY));
        nyxletSession.getMapChannel().remove(INPUT_MAP_CHANNEL_KEY);
    }

    @Override
    public void handleSTROMAResponse (NyxletSession nyxletSession, Map<String, List<String>> baseParameters, STROMAResponseWriter stromaResponseWriter, STROMAResponse stromaResponse) throws Exception {
        if (nyxletSession.containsMapChannelKey(BINARY_INPUT_MAP_CHANNEL_KEY)) {
            String encoding = (baseParameters.containsKey(BINARY_TO_STRING_ENCODING)) ? baseParameters.get(BINARY_TO_STRING_ENCODING).get(0) : "UTF-8";
            handleLocal(nyxletSession, baseParameters, stromaResponseWriter, new String((byte[])nyxletSession.getMapChannelObject(BINARY_INPUT_MAP_CHANNEL_KEY), encoding));
        } else {
            if (!baseParameters.containsKey(LOG_ENTRY_PARAMETER)) throw new CycladesException("Cannot detect parameter: " + LOG_ENTRY_PARAMETER, ResponseCodeEnum.REQUEST_VALIDATION_FAULT.getCode());
            handleLocal(nyxletSession, baseParameters, stromaResponseWriter, baseParameters.get(LOG_ENTRY_PARAMETER).get(0));
        }
    }

    public void handleLocal (NyxletSession sessionDelegate, Map<String, List<String>> baseParameters, STROMAResponseWriter stromaResponseWriter, String entry) throws Exception {
        final String eLabel = "SemanticResourceResponseProcessor.handle: ";
        try {
            LogWriterDelegate logWriter = logWriters.get(baseParameters.get(LOG_ALIAS_PARAMETER).get(0));
            if (logWriter == null) throw new Exception("alias does not exist: " + baseParameters.get(LOG_ALIAS_PARAMETER).get(0));
            logWriter.write(entry);
        } catch (Exception e) {
            handleException(sessionDelegate, stromaResponseWriter, eLabel, e);
        } finally {
            stromaResponseWriter.done();
        }
    }

    @Override
    public boolean isSTROMAResponseCompatible (STROMAResponse response) throws UnsupportedOperationException {
        return false;
    }

    @Override
    public Object[] getMapChannelKeyTargets (NyxletSession nyxletSession) {
        return new Object[]{INPUT_MAP_CHANNEL_KEY};
    }

    private Map<String, LogWriterDelegate> logWriters = new HashMap<String, LogWriterDelegate>();
    public static final String LOG_WRITERS_PROPERTY         = "logWriters";
    public static final String LOG_ENTRY_PARAMETER          = "entry";
    public static final String LOG_ALIAS_PARAMETER          = "alias";
    public static final String BINARY_TO_STRING_ENCODING    = "encoding";
    public static final String INPUT_MAP_CHANNEL_KEY        = "string";
    public static final String BINARY_INPUT_MAP_CHANNEL_KEY = "binary";
    
}
