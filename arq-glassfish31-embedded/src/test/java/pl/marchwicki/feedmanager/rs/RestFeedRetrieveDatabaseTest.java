package pl.marchwicki.feedmanager.rs;

import static com.jayway.jsonassert.JsonAssert.*;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

import java.io.StringWriter;
import java.net.URL;

import javax.inject.Inject;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.jboss.arquillian.persistence.Cleanup;
import org.jboss.arquillian.persistence.TestExecutionPhase;
import org.jboss.arquillian.persistence.UsingDataSet;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ArchivePaths;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.descriptor.api.Descriptors;
import org.jboss.shrinkwrap.descriptor.api.beans10.BeansDescriptor;
import org.jboss.shrinkwrap.descriptor.api.webapp30.WebAppDescriptor;
import org.junit.Test;
import org.junit.runner.RunWith;

import pl.marchwicki.feedmanager.DatabaseFeedsRepository;
import pl.marchwicki.feedmanager.FeedsRepository;
import pl.marchwicki.feedmanager.FeedsService;
import pl.marchwicki.feedmanager.model.Feed;
import pl.marchwicki.feedmanager.model.FeedBuilder;
import pl.marchwicki.feedmanager.model.Item;
import pl.marchwicki.feedmanager.model.entities.FeedEntity;
import pl.marchwicki.feedmanager.model.entities.ItemEntity;

import com.github.kevinsawicki.http.HttpRequest;
import com.sun.jersey.spi.container.servlet.ServletContainer;

@RunWith(Arquillian.class)
public class RestFeedRetrieveDatabaseTest {

	private final String FEED_NAME = "javalobby";
	
	@Inject
	FeedsRepository repository;
	
	@Deployment
	public static WebArchive createDeployment() throws Exception {
		WebAppDescriptor web = Descriptors.create(WebAppDescriptor.class)
				.version("3.0")
				.createServlet()
					.servletName("jersey")
					.servletClass(ServletContainer.class.getName())
					.loadOnStartup(1)
				.up()
				.createServletMapping()
					.servletName("jersey").urlPattern("/*")
				.up();
		
		BeansDescriptor descriptor = Descriptors.create(BeansDescriptor.class)
				.createAlternatives()
					.clazz(DatabaseFeedsRepository.class.getName())
					.up();

		return ShrinkWrap
				.create(WebArchive.class, "test.war")
				.addClass(RestFeedConsumerEndpoint.class)
				.addClasses(FeedsService.class, FeedBuilder.class)
				.addClasses(FeedsRepository.class, DatabaseFeedsRepository.class)
				.addClasses(Feed.class, FeedEntity.class, Item.class, ItemEntity.class)
				.addAsResource("test-persistence.xml", "META-INF/persistence.xml")
				.addAsWebInfResource(new StringAsset(descriptor.exportAsString()), "beans.xml")
				.addAsResource("log4j.properties", ArchivePaths.create("log4j.properties"))
				.setWebXML(new StringAsset(web.exportAsString()));
	}

	@Test
	@InSequence(1)
	@UsingDataSet("datasets/feeds.yml")
	@Cleanup(phase=TestExecutionPhase.NONE)
	public void findFeedById() {
		Feed feed = repository.getFeed(FEED_NAME);
		
		assertThat(feed, is(notNullValue()));
		assertThat(feed.getLink(), equalTo("http://java.dzone.com"));
	}
	
	
	@Test
	@InSequence(2)
	@RunAsClient
	public void shouldReturnValidFeedTest(@ArquillianResource URL baseURL) throws Exception {
		//given
		final StringWriter output = new StringWriter();
		
		//when
		HttpRequest request = HttpRequest.get(baseURL.toURI() + "rs/feed/"+FEED_NAME)
			.receive(output);

		//then
		assertThat(request.code(), equalTo(200));
		with(output.toString()).assertThat("$.feedname", equalTo("javalobby"))
			.assertThat("$.items[0].content", equalTo("first content"))
			.assertThat("$.items[0].link", equalTo("http://java.dzone.com/link/1"))
			.assertThat("$.items[1].content", equalTo("second content"))
			.assertThat("$.items[1].link", equalTo("http://java.dzone.com/link/2"));
	}
	
}
