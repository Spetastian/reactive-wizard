package se.fortnox.reactivewizard.jaxrs;

import se.fortnox.reactivewizard.ExceptionHandler;
import se.fortnox.reactivewizard.MockHttpServerResponse;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import io.reactivex.netty.protocol.http.server.MockHttpServerRequest;
import org.apache.log4j.Appender;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.junit.Test;

import java.nio.channels.ClosedChannelException;
import java.nio.file.FileSystemException;
import java.nio.file.NoSuchFileException;

import static se.fortnox.reactivewizard.test.TestUtil.matches;
import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class ExceptionHandlerTest {

	@Test
	public void shouldLogHeadersForErrors() {
		MockHttpServerRequest request = new MockHttpServerRequest("/path");
		request.addHeader("X-DBID", "5678");
		assertLog(request,
				new RuntimeException("runtime exception"),
				Level.ERROR,
				"500 Internal Server Error\n\tCause: runtime exception\n\tResponse: {\"id\":\"*\",\"error\":\"internal\"}\n\tRequest: GET /path headers: X-DBID=5678 ");
	}

	@Test
	public void shouldLogHeadersForWarnings() {
		MockHttpServerRequest request = new MockHttpServerRequest("/path");
		request.addHeader("X-DBID", "5678");
		assertLog(request,
				new WebException(HttpResponseStatus.BAD_REQUEST),
				Level.WARN,
				"400 Bad Request\n\tCause: -\n\tResponse: {\"id\":\"*\",\"error\":\"badrequest\"}\n\tRequest: GET /path headers: X-DBID=5678 ");
	}

	@Test
	public void shouldNotLog404Errors() {
		assertNoLog(new MockHttpServerRequest("/path"),
				new NoSuchFileException(""));
		assertNoLog(
				new MockHttpServerRequest("/path"),
				new FileSystemException(""));
	}

	@Test
	public void shouldLogClosedChannelExceptionAtDebugLevel() {
		assertLog(new MockHttpServerRequest("/path"),
				new ClosedChannelException(),
				Level.DEBUG,
				"ClosedChannelException: GET /path");
	}

	private void assertLog(HttpServerRequest<ByteBuf> request, Exception e, Level expectedLevel,
			String expectedLog) {
		Appender mockAppender = mock(Appender.class);
		LogManager.getLogger(ExceptionHandler.class).addAppender(mockAppender);

		HttpServerResponse<ByteBuf> response = new MockHttpServerResponse();
		new ExceptionHandler().handleException(request, response, e);

		String regex = expectedLog.replaceAll("\\*", ".*")
				.replaceAll("\\[", "\\\\[")
				.replaceAll("\\]", "\\\\]")
				.replaceAll("\\{", "\\\\{")
				.replaceAll("\\}", "\\\\}");

		verify(mockAppender).doAppend(matches(event->{
			assertThat(event.getLevel()).isEqualTo(expectedLevel);
			assertThat(event.getMessage().toString()).matches(regex);
		}));
	}

	private void assertNoLog(HttpServerRequest<ByteBuf> request, Exception e) {
		Appender mockAppender = mock(Appender.class);
		LogManager.getLogger(ExceptionHandler.class).addAppender(mockAppender);

		HttpServerResponse<ByteBuf> response = new MockHttpServerResponse();
		new ExceptionHandler().handleException(request, response, e);

		verify(mockAppender, times(0)).doAppend(any());
	}

}