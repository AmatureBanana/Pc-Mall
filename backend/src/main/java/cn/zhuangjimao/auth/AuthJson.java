package cn.zhuangjimao.auth;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class AuthJson {
    @Bean
    Jackson2ObjectMapperBuilderCustomizer strictRequestFields() {
        return builder -> builder.featuresToEnable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .postConfigurer(mapper -> {
                    var text = mapper.coercionConfigFor(LogicalType.Textual);
                    text.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
                    text.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
                    text.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
                });
    }
}
