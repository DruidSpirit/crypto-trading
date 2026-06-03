package druid.elf.tool.util;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.yitter.contract.IdGeneratorOptions;
import com.github.yitter.idgen.YitIdHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.AntPathMatcher;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

@SuppressWarnings("unused")
@Slf4j
public class UtilForData {


    public static <T> Predicate<T> distinctByKey(Function<? super T, Object> keyExtractor) {
        Map<Object, Boolean> seen = new ConcurrentHashMap<>();
        return t -> seen.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
    }


    public static <T extends Enum<?>, R> T getEnumByCode ( T[] ts, Function<T,R> function, R r ) {

        for ( T t : ts ) {
            if ( function.apply(t).equals(r)) return t;
        }
        log.warn("Status code {} has no matching enum", r);

        return null;
    }


    public static boolean pathMatch(Collection<String> patterns,String path){

        for ( String pattern : patterns ) {
            AntPathMatcher antPathMatcher = new AntPathMatcher();
            if ( antPathMatcher.match(pattern, path) ) {
                return true;
            }
        }
        return false;
    }


    public static ObjectMapper getCommonObjectMapper(){

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,false);
        objectMapper.registerModule(new JavaTimeModule());
        return objectMapper;
    }


    public static <T> T transJsonStringToT( String jsonStr, Class<T> tClass ){

        if (StrUtil.isBlank(jsonStr)) return null;

        try {
            return getCommonObjectMapper().readValue(jsonStr,tClass);
        } catch (JsonProcessingException e) {
            log.warn("jsonStr read fail",e);
            return null;
        }
    }


    public static <T> T transJsonObjectToT( Object jsonObject, Class<T> tClass ){

        if ( jsonObject == null ) return null;

        try {
            return transJsonStringToT(getCommonObjectMapper().writeValueAsString(jsonObject),tClass);
        } catch (JsonProcessingException e) {
            log.warn("jsonObject write fail",e);
            return null;
        }
    }


    public static String transObjectToJsonString( Object object ){

        if ( object == null ) return null;

        try {
            return getCommonObjectMapper().writeValueAsString(object);
        } catch (JsonProcessingException e) {
            log.warn("jsonObject write fail",e);
            return null;
        }
    }

    static {

        IdGeneratorOptions options = new IdGeneratorOptions((short) 1);



        YitIdHelper.setIdGenerator(options);
    }

    public static String getSnowId(){


        return YitIdHelper.nextId()+"";
    }

}
