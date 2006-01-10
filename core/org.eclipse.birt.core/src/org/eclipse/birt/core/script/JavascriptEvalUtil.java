/*
 *************************************************************************
 * Copyright (c) 2005 Actuate Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Actuate Corporation  - initial API and implementation
 *  
 *************************************************************************
 */ 
package org.eclipse.birt.core.script;

import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.birt.core.exception.BirtException;
import org.eclipse.birt.core.exception.CoreException;
import org.eclipse.birt.core.i18n.ResourceConstants;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.IdScriptableObject;
import org.mozilla.javascript.NativeJavaObject;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Undefined;

/**
 * Utilities to faciliate the evaluation of Javascript expressions. Handles common evaluation
 * tasks like exception handling, data type conversion and script caching
 */
public class JavascriptEvalUtil
{
	private static Logger logger = Logger.getLogger( JavascriptEvalUtil.class.getName( ) );

	/*
	 * LRU cache for compiled scripts. For performance reasons, scripts are compiled and put 
	 * in a cache. Repeated evaluation of the same script will then used the compiled binary.
	 * 
	 */
	static protected final int SCRIPT_CACHE_SIZE = 200;
	// access-ordered LRU cache
	static protected Map compiledScriptCache = Collections.synchronizedMap( 
		new LinkedHashMap( SCRIPT_CACHE_SIZE, (float)0.75, true)
		{
			protected boolean removeEldestEntry(Map.Entry eldest) {
	         return size() > SCRIPT_CACHE_SIZE;
	      }		
		} );
	
	
	/** Evaluates Javascript expression and return its result, doing the necessary
	 *  Javascript -> Java data type conversion if necessary
	 * @param cx Javascript context. If null, current thread's context is used
	 * @param scope Javascript scope to evaluate script in
	 * @param scriptText text of Javascript expression
	 * @param source descriptive text of script source (for error reporting)
	 * @param lineNo line number of script in it source
	 * @return Evaluation result. 
	 * @throws BirtException If evaluation failed
	 */
	public static Object evaluateScript(Context cx, Scriptable scope,
			String scriptText, String source, int lineNo)
			throws BirtException
	{
		Object result = null;

		// Use provided context, or get the thread context if none provided
		boolean enterContext = cx == null;
		if ( enterContext )
			cx = Context.enter();
		
		try
		{
			Script compiledScript = getCompiledScript( cx, scope,
					scriptText,
					source,
					lineNo );
			result = compiledScript.exec( cx, scope );
		}
		catch ( RhinoException e)
		{
			// Note: use the real source and lineNo here. The source and lineNo reported
			// by e can be wrong, since we may be executing an identical compiled script
			// from a different source/line
			throw wrapRhinoException( e, scriptText, source, lineNo );
		}
		finally
		{
			if ( enterContext)
				Context.exit();
		}
		
		return convertJavascriptValue(result);
	}
	
	/**
	 * Gets a compiled script, using and updating the script cache if necessary
	 */
	protected static Script getCompiledScript( Context cx, Scriptable scope,
			String scriptText, String source, int lineNo )
	{
		assert scriptText != null;
		
		Script compiledScript = (Script) compiledScriptCache.get( scriptText );
		if ( compiledScript == null )
		{
			compiledScript = cx.compileString( scriptText, source, lineNo, null );
			compiledScriptCache.put( scriptText, compiledScript );
		}

		return compiledScript;
	}
	
    /**
     * Creates Javascript native wrapper for Java objects, if necessary.
     * This method currently only wraps Date/time objects. Rhino engine
     * natively handles wrapping String, Number and Boolean objects. 
     * @param value Java object to convert from
     * @scope A javascript scope with the proper native JS constructors defined
     */
    public static Object convertToJavascriptValue( Object value, Scriptable scope  )
    {
    	if ( value instanceof Date)
    	{
    		// Wrap in Javascript native Date class
    		Context cx = Context.enter();
    		try
    		{
    			// Javascript and Java Date has the same conversion to/from a Long value
    			Long timeVal = new Long(((Date) value).getTime());
    			return ScriptRuntime.newObject( cx, scope, 
    					"Date", 
    					new Object[]{ timeVal } );
    		}
    		finally
    		{
    			Context.exit();
    		}
    	}
    	else
    		return value;
    }
    
	/**
	 * Handles a Rhino script evaluation result, converting Javascript native objects
	 * into equivalent Java objects if necessary.
	 * @param inputObj Object returned by rhino engine.
	 * @return If inputObj is a native Javascript object, its equivalent Java object 
	 *   is returned; otherwise inputObj is returned
	 */
	public static Object convertJavascriptValue(Object inputObj)
	{
		if ( inputObj instanceof Undefined )
		{
			return null;
		}
		if ( inputObj instanceof IdScriptableObject ) 
		{
			// Return type is possibly a Javascript native object
			// Convert to Java object with same value
			String jsClass = ((Scriptable) inputObj).getClassName();
			if ( "Date".equals(jsClass) ) 
			{
					return new Date( (long) Context.toNumber( inputObj ) );
			} 
			else if ( "Boolean".equals(jsClass)) 
			{
				return new Boolean(Context.toBoolean(inputObj));
			} 
			else if ( "Number".equals(jsClass)) 
			{
				return new Double(Context.toNumber(inputObj));
			} 
			else if( "String".equals(jsClass) )
			{
				return inputObj.toString();
			}
		}
		else if ( inputObj instanceof NativeJavaObject )
		{
		    return ( (NativeJavaObject) inputObj ).unwrap( );
		}
		
		return inputObj;
	}

	/**
	 * Converts Rhino exception (a runtime exception) to BirtException
	 * @param e Rhino exception
	 * @param scriptText Javascript code which resulted in the exception (for error reporting purpose)
	 * @param source description of the source script. If null, get this info from Rhino exception
	 * @param lineNo lineNo of error location
	 * @throws 
	 */
	public static BirtException wrapRhinoException( RhinoException e, String scriptText, 
			String source, int lineNo ) 
	{
		if ( source == null )
		{
			// Note that sourceName from RhinoException sometimes get truncated (need to find out why)
			// Better some than nothing
			source = e.sourceName();
			lineNo = e.lineNumber();
		}
		
		if ( logger.isLoggable( Level.FINE ) )
			logger.log( Level.FINE, 
					"Unexpected RhinoException. Source=" + source + ", line=" + lineNo+ ", Script=\n"
					+ scriptText + "\n",
					e );

        return new CoreException( ResourceConstants.JAVASCRIPT_ERROR,
				new Object[]{
        		e.getLocalizedMessage(), source,  new Integer( lineNo ), scriptText 
				},
				e);
	}
}
