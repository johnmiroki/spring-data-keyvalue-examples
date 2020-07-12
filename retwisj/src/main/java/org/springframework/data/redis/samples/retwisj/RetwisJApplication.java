package org.springframework.data.redis.samples.retwisj;

import org.apache.catalina.Context;
import org.apache.tomcat.util.descriptor.web.JspConfigDescriptorImpl;
import org.apache.tomcat.util.descriptor.web.JspPropertyGroup;
import org.apache.tomcat.util.descriptor.web.JspPropertyGroupDescriptorImpl;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;

import java.util.Collections;

/**
 * @author John
 * @date 2020/7/10
 */
@SpringBootApplication
public class RetwisJApplication extends SpringBootServletInitializer {

    public static void main(String[] args) {
        SpringApplication.run(RetwisJApplication.class, args);
    }

/*
    <jsp-config>
		<jsp-property-group>
        <description>Basic header/footer templating</description>
		  <url-pattern>*.jsp</url-pattern>
  		  <el-ignored>false</el-ignored>
		  <include-prelude>/WEB-INF/templates/header.jspf</include-prelude>
		  <include-coda>/WEB-INF/templates/footer.jspf</include-coda>
		</jsp-property-group>
	</jsp-config>
	*/
    @Bean
    public ConfigurableServletWebServerFactory configurableServletWebServerFactory ( ) {
        return new TomcatServletWebServerFactory() {
            @Override
            protected void postProcessContext(Context context) {
                super.postProcessContext(context);
                JspPropertyGroup jspPropertyGroup = new JspPropertyGroup();
                jspPropertyGroup.addUrlPattern("*.jsp");
//                jspPropertyGroup.addUrlPattern("*.jspf");
//                jspPropertyGroup.setPageEncoding("UTF-8");
//                jspPropertyGroup.setScriptingInvalid("false");
                jspPropertyGroup.setElIgnored("false");
                jspPropertyGroup.addIncludePrelude("/WEB-INF/templates/header.jspf");
                jspPropertyGroup.addIncludeCoda("/WEB-INF/templates/footer.jspf");
//                jspPropertyGroup.setTrimWhitespace("true");
//                jspPropertyGroup.setDefaultContentType("text/html");
                JspPropertyGroupDescriptorImpl jspPropertyGroupDescriptor = new JspPropertyGroupDescriptorImpl(jspPropertyGroup);
                context.setJspConfigDescriptor(new JspConfigDescriptorImpl(Collections.singletonList(jspPropertyGroupDescriptor), Collections.emptyList()));
            }
        };
    }
}
