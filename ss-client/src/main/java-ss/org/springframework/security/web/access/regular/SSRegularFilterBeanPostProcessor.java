package org.springframework.security.web.access.regular;

import java.util.List;

import javax.servlet.Filter;

import org.apache.log4j.Logger;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.SwitchControl;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;

import com.dianrong.common.uniauth.common.client.DomainDefine;
import com.dianrong.common.uniauth.common.client.DomainDefine.CasPermissionControlType;
import com.dianrong.common.uniauth.common.util.ReflectionUtils;

/**
 * 用于在服务启动的时候将SSRegularPermissionFilter注入到spring security的处理链路中
 * @author wanglin
 */
public class SSRegularFilterBeanPostProcessor implements BeanPostProcessor, Ordered, SwitchControl, InitializingBean{
	/**.
	 * logger
	 */
	private static final Logger logger = Logger.getLogger(SSRegularFilterBeanPostProcessor.class);
	
	/**.
	 * spring security处理filter链的bean name
	 */
	private static final String SPRING_SECURITY_FILETER_BEAN_NAME="org.springframework.security.filterChainProxy";
	
	/**.
	 *   需要关注的最终处理spring security的权限的filter
	 */
	private static final String SPRING_SECURITY_FILTERSECURITYINTERCEPTOR_CLASS_NAME="org.springframework.security.web.access.intercept.FilterSecurityInterceptor";
	
	@Autowired
	private DomainDefine domainDefine;
	
	@Autowired
	private SSRegularPermissionFilter regularPermissionFilter;
	
	/**.
	 * 最低优先级，最后执行
	 */
	@Override
	public int getOrder() {
		return LOWEST_PRECEDENCE;
	}

	/**.
	 * do not process
	 */
	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		return bean;
	}

	/**.
	 * update bean field
	 * 注入uniauth的正则处理权限的filter
	 */
	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		if (!isOn()) {
			return bean;
		}
		if(SPRING_SECURITY_FILETER_BEAN_NAME.equals(beanName) && bean instanceof FilterChainProxy) {
			FilterChainProxy  _filterChainProxy = (FilterChainProxy)bean;
			List<SecurityFilterChain> filters = _filterChainProxy.getFilterChains();
			if (filters == null) {
				throw new RuntimeException("bean ["+SPRING_SECURITY_FILETER_BEAN_NAME+"] is invalid, the  field filterChains is null");
			}
			DefaultSecurityFilterChain destFilterChain = null;
			// 遍历查找 带有AnyRequestMatcher的SecurityFilterChain,将SSRegularPermissionFilter加入进去
			for(SecurityFilterChain securityFilter : filters) {
				if (securityFilter instanceof DefaultSecurityFilterChain) {
					DefaultSecurityFilterChain _securityFilter = (DefaultSecurityFilterChain)securityFilter;
					if (_securityFilter.getRequestMatcher() instanceof AnyRequestMatcher) {
						destFilterChain = _securityFilter;
						break;
					}
				}
			}
			// add one 
			if (destFilterChain == null) {
				@SuppressWarnings("unchecked")
				List<SecurityFilterChain> _filterChains = (List<SecurityFilterChain>)ReflectionUtils.getField(_filterChainProxy, "filterChains", false);
				_filterChains.add(new DefaultSecurityFilterChain(AnyRequestMatcher.INSTANCE, regularPermissionFilter));
			} else {
				int index = -1;
				List<Filter> _filters =  destFilterChain.getFilters();
				for (Filter _filter : _filters) {
					if (_filter.getClass().equals(SPRING_SECURITY_FILTERSECURITYINTERCEPTOR_CLASS_NAME)) {
						break;
					}
					index++;
				}
				// 放到org.springframework.security.web.access.intercept.FilterSecurityInterceptor 这个filter前面
				_filters.add(index, regularPermissionFilter);
			}
		}
		return bean;
	}

	@Override
	public boolean isOn() {
		return domainDefine.controlTypeSupport(CasPermissionControlType.REGULAR_PATTERN);
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		if (this.regularPermissionFilter == null) {
			logger.error("regularPermissionFilter is null , check the xml configuration");
			throw new RuntimeException("regularPermissionFilter can not be null");
		}
	}
}
