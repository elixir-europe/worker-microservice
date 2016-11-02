package eu.crg.ega.workerservice.config;

import eu.crg.ega.microservice.filter.LoginTypeUsernamePasswordAuthenticationTokenFilter;
import eu.crg.ega.microservice.filter.RestTokenPreAuthenticatedProcessingFilter;
import eu.crg.ega.microservice.security.CustomSimpleUrlFailureHandler;
import eu.crg.ega.microservice.security.LoginFormAuthenticationProvider;
import eu.crg.ega.microservice.security.LoginFormAuthenticationSuccessHandler;
import eu.crg.ega.microservice.security.RestAuthenticationEntryPoint;
import eu.crg.ega.microservice.security.RestTokenAuthenticationUserDetailsService;
import eu.crg.ega.microservice.security.RestWebAuthenticationDetailsSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationProvider;

@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig extends WebSecurityConfigurerAdapter {

  @Autowired
  Environment environment;

  //LOGIN "FORM" for rest authentication provider and filter
  @Bean
  public SimpleUrlAuthenticationFailureHandler failureHandler() {
    return new CustomSimpleUrlFailureHandler();
  }

  @Bean
  public LoginFormAuthenticationSuccessHandler loginFormAuthenticationSuccessHandler() {
    return new LoginFormAuthenticationSuccessHandler();
  }

  @Bean
  public LoginTypeUsernamePasswordAuthenticationTokenFilter loginTypeUsernamePasswordAuthenticationTokenFilter() {
    return new LoginTypeUsernamePasswordAuthenticationTokenFilter(
        loginFormAuthenticationSuccessHandler());
  }

  @Bean
  public RestAuthenticationEntryPoint restAuthenticationEntryPoint() {
    return new RestAuthenticationEntryPoint();
  }

  @Bean
  public LoginFormAuthenticationProvider loginFormAuthenticationProvider() {
    LoginFormAuthenticationProvider provider = new LoginFormAuthenticationProvider();
    return provider;
  }
  //END LOGIN "FORM" for rest

  // REST token authentication Provider and filter
  @Bean
  public AuthenticationDetailsSource restWebAuthenticationDetailsSource() {
    return new RestWebAuthenticationDetailsSource();
  }

  @Bean
  public RestTokenPreAuthenticatedProcessingFilter restTokenPreAuthenticatedProcessingFilter(
      final AuthenticationManager authenticationManager) {
    RestTokenPreAuthenticatedProcessingFilter filter = new RestTokenPreAuthenticatedProcessingFilter();
    filter.setAuthenticationManager(authenticationManager);
    filter.setInvalidateSessionOnPrincipalChange(true);
    filter.setCheckForPrincipalChanges(false);
    filter.setContinueFilterChainOnUnsuccessfulAuthentication(false);
    filter.setAuthenticationDetailsSource(restWebAuthenticationDetailsSource());
    return filter;
  }

  @Bean
  public RestTokenAuthenticationUserDetailsService restTokenAuthenticationUserDetailsService() {
    return new RestTokenAuthenticationUserDetailsService();
  }

  @Bean
  public AuthenticationProvider restTokenAuthenticationProvider() {
    PreAuthenticatedAuthenticationProvider provider = new PreAuthenticatedAuthenticationProvider();
    provider.setPreAuthenticatedUserDetailsService(restTokenAuthenticationUserDetailsService());
    return provider;
  }
  //END REST token authentication Provider

  //Access Authentication Manager Bean
  @Bean
  @Override
  public AuthenticationManager authenticationManagerBean() throws Exception {
    return super.authenticationManagerBean();
  }
  // END Access Authentication Manager Bean

  //CONFIGURATION
  @Autowired
  public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
    // Add auth provider for token
    auth.authenticationProvider(restTokenAuthenticationProvider());
    // Add auth provider for loginForm
    auth.authenticationProvider(loginFormAuthenticationProvider());
  }

  @Override
  protected void configure(HttpSecurity http) throws Exception {

    String apiVersion = environment.getProperty("server.servlet-path");

    http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS); //Do not create sessions

    http.formLogin().disable();

    http.csrf().disable();

    http.authorizeRequests()
        .antMatchers(apiVersion +"/info").hasAnyRole("ADMIN", "SYSTEM", "SYSTEM_BASIC")
        .antMatchers(apiVersion +"/metrics/**").hasAnyRole("ADMIN", "SYSTEM", "SYSTEM_BASIC")
        .antMatchers(apiVersion +"/dump").hasAnyRole("ADMIN", "SYSTEM", "SYSTEM_BASIC")
        .antMatchers(apiVersion +"/trace").hasAnyRole("ADMIN", "SYSTEM", "SYSTEM_BASIC")
        .antMatchers(apiVersion +"/mappings").hasAnyRole("ADMIN", "SYSTEM", "SYSTEM_BASIC")
        .antMatchers(apiVersion +"/config/**").hasAnyRole("ADMIN", "SYSTEM", "SYSTEM_BASIC")
        .antMatchers(apiVersion +"/autoconfig").hasAnyRole("ADMIN", "SYSTEM", "SYSTEM_BASIC")
        .antMatchers(apiVersion +"/beans").hasAnyRole("ADMIN", "SYSTEM", "SYSTEM_BASIC")
        .antMatchers(apiVersion +"/health").hasAnyRole("ADMIN", "SYSTEM", "SYSTEM_BASIC")
        .antMatchers(apiVersion +"/configprops").hasAnyRole("ADMIN", "SYSTEM", "SYSTEM_BASIC")
        .antMatchers(apiVersion +"/login").permitAll();

    http.addFilterBefore(restTokenPreAuthenticatedProcessingFilter(authenticationManagerBean()),
        UsernamePasswordAuthenticationFilter.class);
  }
  //END CONFIGURATION
}
