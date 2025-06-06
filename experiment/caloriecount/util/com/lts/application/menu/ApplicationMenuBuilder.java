//  Copyright 2006, Clark N. Hobbie
//
//  This file is part of the com.lts.application library.
//
//  The com.lts.application library is free software; you can redistribute it
//  and/or modify it under the terms of the Lesser GNU General Public License
//  as published by the Free Software Foundation; either version 2.1 of the
//  License, or (at your option) any later version.
//
//  The com.lts.application library is distributed in the hope that it will be
//  useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
//  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU
//  General Public License for more details.
//
//  You should have received a copy of the Lesser GNU General Public License
//  along with the com.lts.application library; if not, write to the Free
//  Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
//  02110-1301 USA
//
package com.lts.application.menu;

import java.awt.event.ActionListener;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import com.lts.LTSException;
import com.lts.lang.reflect.ReflectionUtils;
import com.lts.swing.menu.MenuBuilder;

/**
 * A MenuBuilder that responds to menu item selection by calling a method 
 * on an instance of a class.
 * 
 * <P>
 * This method implements {@link MenuBuilder#getActionListener(Object, String)} 
 * by getting all the methods associated with the class of the receiver, and then 
 * trying to find one whose name is the same as the provided value.  Only methods
 * that return nothing (i.e., null) and that accept no parameters are considered 
 * when trying to find a match.
 * 
 * <P>
 * The class uses {@link ApplicationMenuAction} to perform the actual invocation 
 * of the method at the time of menu item selection.
 * 
 * @author cnh
 * @see MenuBuilder
 * @see MenuBuilder#getActionListener(Object, String)
 * @see ApplicationMenuAction
 */
public class ApplicationMenuBuilder extends MenuBuilder
{
	protected Map<String, Method> myMethodMap;

	protected Map getNameToMethod (Object receiver)
	{
		if (null == myMethodMap)
			myMethodMap = buildMethodMap(receiver);
		
		return myMethodMap;
	}

	protected void initializeMethodMap (Object o)
	{
		myMethodMap = buildMethodMap(o);
	}
	
	
	/**
	 * Given a receiver and the name of a method, get the corresponding ActionListener.
	 * 
	 * <P>
	 * See the description for this class, {@link ApplicationMenuBuilder}, for details
	 * of how this method works.
	 * 
	 * @param receiver
	 *        The object whose method should be invoked in response the
	 *        {@link ActionListener#actionPerformed(java.awt.event.ActionEvent)}.
	 * @param methodName
	 *        The name of the method to invoke in response to actionPerformed being
	 *        called.
	 *        
	 * @see MenuBuilder#getActionListener(Object, String)
	 */
	protected ActionListener getActionListener(Object receiver, String methodName)
			throws LTSException
	{
		Method method = (Method) getNameToMethod(receiver).get(methodName);
		if (null == method)
		{
			String cname = "unknown class";
			
			if (null != receiver)
				cname = receiver.getClass().getName();
			
			String msg =
				"The method, " + methodName 
				+ ", does not exist in class " + cname;
			
			throw new LTSException(msg);
		}

		ActionListener listener = new ApplicationMenuAction(receiver, method);
		return listener;
	}

	/**
	 * Given a class, build a map from names to no-argument methods.
	 * 
	 * <H2>Usage</H2>
	 * <CODE>
	 * <PRE>
	 * Object o = &lt;some value&gt;
	 * Map nameToMethod = buildMethodMap(o);
	 * </PRE>
	 * </CODE>
	 * 
	 * <H2>Description</H2>
	 * This method creates a map from method names (java.lang.String) to the methods
	 * that implement them (java.reflect.Method) for a particular class such that 
	 * the methods accept no arguments.  If the class defines both a static and an 
	 * instance method of the same name that takes no arguments, then this method 
	 * does not guarantee that it returns the static or the instance method in the 
	 * map.
	 * 
	 * <P>
	 * The map is case sensitive.
	 * 
	 * @param receiver The object to build the map for.  This should be non-null.
	 * @return A map from method name to method, as described above.
	 */
	protected Map<String, Method> buildMethodMap (Object receiver)
	{
		Map<String, Method> map = new HashMap();
		
		Class clazz = receiver.getClass();
		Method[] methods = ReflectionUtils.getAllMethods(clazz);
		for (int i = 0; i < methods.length; i++)
		{
			Method method = methods[i];
			if (method.getParameterTypes().length < 1)
				map.put(methods[i].getName(), methods[i]);
		}
		
		return map;
	}
}
