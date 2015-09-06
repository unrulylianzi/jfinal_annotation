/**
 * Copyright (c) 2011-2015, James Zhan 詹波 (jfinal@126.com).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jfinal.core;

import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


import com.jfinal.config.Constants;
import com.jfinal.config.JFinalConfig;
import com.jfinal.handler.Handler;
import com.jfinal.log.Logger;

/**
 * JFinal framework filter
 */
public final class JFinalFilter implements Filter {
	
	private Handler handler;
	private String encoding;
	private JFinalConfig jfinalConfig;
	private Constants constants;
	private static final JFinal jfinal = JFinal.me();
	private static Logger log;
	private int contextPathLength;

	/**
	 * 实现init方法，通过servlet规范配置filter的属性，configClass，这就是整个项目的配置文件，定义一些配置，TODO,可以实现分布式的配置。
	 * jetty容器启动的时候会根据web.xml的配置，调用init()方法，加载信息只会执行一次。
	 * */
	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		//创建JFinalConfig对象
		createJFinalConfig(filterConfig.getInitParameter("configClass"));

		//初始化插件和其他资源
		if (jfinal.init(jfinalConfig, filterConfig.getServletContext()) == false)
			throw new RuntimeException("JFinal init error!");
		
		handler = jfinal.getHandler();
		constants = Config.getConstants();
		encoding = constants.getEncoding();
		//系统启动之后做的相关工作,如果需要实现，可以实现相应的回调函数
		jfinalConfig.afterJFinalStart();

		//contextPath的值 TODO
		String contextPath = filterConfig.getServletContext().getContextPath();
		contextPathLength = (contextPath == null || "/".equals(contextPath) ? 0 : contextPath.length());
	}
	/**
	 * 实现dofilter方法，
	 * */
	public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
		HttpServletRequest request = (HttpServletRequest)req;
		HttpServletResponse response = (HttpServletResponse)res;
		request.setCharacterEncoding(encoding);
		
		String target = request.getRequestURI();
		if (contextPathLength != 0)
			target = target.substring(contextPathLength);
		//Handler拥有web请求的绝对控制权，一个handler chain在配置类里面配置好，会按照这个chain根据handler的逻辑，决定是否走下去
		//数组传值，关于传值和引用向来有争议
		boolean[] isHandled = {false};
		try {
			//责任链模式
			handler.handle(target, request, response, isHandled);
		}
		catch (Exception e) {
			if (log.isErrorEnabled()) {
				String qs = request.getQueryString();
				log.error(qs == null ? target : target + "?" + qs, e);
			}
		}
		//如果isHandled[0]为假则继续
		if (isHandled[0] == false)
			chain.doFilter(request, response);
	}

	/**
	 * 实现destroy方法，容器关闭时调用destroy()方法进行销毁。
	 * JFinalConfig 中的 afterJFinalStart()与 beforeJFinalStop()方法供开发者在 JFinalConfig 继承类中 覆盖。reference:
	 * jfinal-2.0-manual.pdf 2.7节描述
	 * */
	public void destroy() {
		//系统关闭之后做的清理工作,如果需要实现，可以实现相应的回调函数
		jfinalConfig.beforeJFinalStop();
		//停止相应的插件，关闭相应的资源，plugin一般都要实现stop方法，释放资源
		jfinal.stopPlugins();
	}

	/**
	 * <p>通过传递的配置文件的位置，a_little_config.txt的例子</p>
	 * example：<pre>
	 *      <filter>
				 <filter-name>jfinal</filter-name>
				 <filter-class>com.jfinal.core.JFinalFilter</filter-class>
				 <init-param>
					 <param-name>configClass</param-name>
					 <param-value>com.demo.common.DemoConfig</param-value>
				 </init-param>
			 </filter>
	 * </pre>
	 * */
	private void createJFinalConfig(String configClass) {
		if (configClass == null)
			throw new RuntimeException("Please set configClass parameter of JFinalFilter in web.xml");
		
		Object temp = null;
		try {
			//通过反射创建配置文件对应的类，比如DemoConfig
			temp = Class.forName(configClass).newInstance();
		} catch (Exception e) {
			throw new RuntimeException("Can not create instance of class: " + configClass, e);
		}
		
		if (temp instanceof JFinalConfig)
			jfinalConfig = (JFinalConfig)temp;
		else
			throw new RuntimeException("Can not create instance of class: " + configClass + ". Please check the config in web.xml");
	}
	
	static void initLogger() {
		log = Logger.getLogger(JFinalFilter.class);
	}
}
