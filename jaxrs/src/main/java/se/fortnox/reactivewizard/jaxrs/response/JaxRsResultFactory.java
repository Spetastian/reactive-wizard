package se.fortnox.reactivewizard.jaxrs.response;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import rx.Observable;
import rx.Single;
import rx.functions.Func1;
import se.fortnox.reactivewizard.jaxrs.Headers;
import se.fortnox.reactivewizard.jaxrs.JaxRsResource;
import se.fortnox.reactivewizard.jaxrs.SuccessStatus;
import se.fortnox.reactivewizard.json.JsonSerializerFactory;
import se.fortnox.reactivewizard.util.ReflectionUtil;

import javax.ws.rs.core.MediaType;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

public class JaxRsResultFactory<T> {

    private static final Charset charset        = Charset.forName("UTF-8");
    private static final Class   BYTEARRAY_TYPE = (new byte[0]).getClass();
    protected final HttpResponseStatus responseStatus;
    protected final Class<T>           rawReturnType;
    protected final Func1<T, byte[]>   serializer;
    protected final Map<String, Object> headers = new HashMap<>();
    private final ResultTransformer<T> transformers;

    public JaxRsResultFactory(JaxRsResource<T> resource, ResultTransformerFactories resultTransformerFactories, JsonSerializerFactory jsonSerializerFactory) {
        Method method = resource.getResourceMethod();
        responseStatus = getSuccessStatus(resource);
        rawReturnType = getRawReturnType(method);
        serializer = createSerializer(resource.getProduces(), rawReturnType, jsonSerializerFactory);

        transformers = resultTransformerFactories.createTransformers(resource);

        headers.put("Content-Type", resource.getProduces());

        Headers headerAnnotation = resource.getInstanceMethod().getAnnotation(Headers.class);

        if (headerAnnotation != null && headerAnnotation.value().length > 0) {
            for (String headerString : headerAnnotation.value()) {
                if (headerString.contains(":")) {
                    final String[] parts = headerString.split(":");
                    headers.put(parts[0].trim(), parts[1].trim());
                }
            }
        }
    }

    public JaxRsResult<T> createResult(Observable<T> output, Object[] args) {
        return new JaxRsResult<>(output,
            responseStatus,
            serializer,
            headers
        );
    }

    public JaxRsResult<T> create(Observable<T> output, Object[] args) {
        JaxRsResult<T> result = createResult(output, args);
        result = transformers.apply(result, args);
        return result;
    }

    private Func1<T, byte[]> createSerializer(String type, Class<T> dataCls, JsonSerializerFactory jsonSerializerFactory) {
        if (type.equals(MediaType.APPLICATION_JSON)) {
            return jsonSerializerFactory.createByteSerializer(dataCls)::apply;
        }
        if (dataCls.equals(BYTEARRAY_TYPE)) {
            return data -> (byte[])data;
        }
        return data -> data.toString().getBytes(charset);
    }

    @SuppressWarnings("unchecked")
    private Class<T> getRawReturnType(Method method) {
        if (Observable.class.isAssignableFrom(method.getReturnType()) || Single.class.isAssignableFrom(method.getReturnType())) {
            return (Class<T>)ReflectionUtil.getRawType(ReflectionUtil.getTypeOfObservable(method));
        }
        return (Class<T>)method.getReturnType();
    }

    private HttpResponseStatus getSuccessStatus(JaxRsResource resource) {
        SuccessStatus successStatus = ReflectionUtil.getAnnotation(resource.getResourceMethod(), SuccessStatus.class);
        if (successStatus != null) {
            return HttpResponseStatus.valueOf(successStatus.value());
        }

        if (resource.getHttpMethod().equals(HttpMethod.POST)) {
            return HttpResponseStatus.CREATED;
        }

        return HttpResponseStatus.OK;
    }
}
