/*
 * Copyright 2010-2017 interactive instruments GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import de.interactive_instruments.IFile;
import de.interactive_instruments.UriUtils;
import de.interactive_instruments.etf.dal.dto.capabilities.ResourceDto;
import de.interactive_instruments.etf.dal.dto.capabilities.TestObjectDto;
import de.interactive_instruments.etf.detector.DetectedTestObjectType;
import de.interactive_instruments.etf.detector.IncompatibleTestObjectTypeException;
import de.interactive_instruments.etf.detector.TestObjectTypeDetectorManager;
import de.interactive_instruments.etf.detector.TestObjectTypeNotDetected;
import de.interactive_instruments.etf.model.capabilities.SecuredResource;
import de.interactive_instruments.etf.model.capabilities.StdResource;
import de.interactive_instruments.etf.model.capabilities.TestObjectType;
import de.interactive_instruments.exceptions.ObjectWithIdNotFoundException;
import jdk.nashorn.internal.runtime.URIUtils;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class StdTestObjectTypeDetectorTest {

	@Test
	public void testWfs20() throws URISyntaxException, IOException, TestObjectTypeNotDetected {
		final DetectedTestObjectType detectedType = TestObjectTypeDetectorManager.detect(
				new StdResource("test",
						"https://services.interactive-instruments.de/cite-xs-49/simpledemo/cgi-bin/cities-postgresql/wfs"
								+ "?request=GetCapabilities&service=wfs"));
		assertNotNull(detectedType);
		assertEquals("9b6ef734-981e-4d60-aa81-d6730a1c6389", detectedType.getId().toString());
		assertEquals("db12feeb-0086-4006-bc74-28f4fdef0171", detectedType.getParent().getId().toString());

		final TestObjectDto testObject = new TestObjectDto();
		testObject.addResource(new ResourceDto("serviceEndpoint", "http://none"));
		detectedType.enrichAndNormalize(testObject);

		assertEquals("SimpleDemo WFS", testObject.getLabel());
		assertEquals("SimpleDemo WFS by XtraServer", testObject.getDescription());
		assertEquals("https://services.interactive-instruments.de/cite-xs-49/simpledemo/cgi-bin/cities-postgresql/wfs"
				+ "?ACCEPTVERSIONS=2.0.0&request=GetCapabilities&service=wfs", testObject.getResourceByName("serviceEndpoint").toString());
	}

	@Test
	public void testWfs11() throws URISyntaxException, IOException, TestObjectTypeNotDetected,
			ObjectWithIdNotFoundException, IncompatibleTestObjectTypeException {
		final DetectedTestObjectType detectedType = TestObjectTypeDetectorManager.detect(
				new StdResource("test",
						"https://services.interactive-instruments.de/cite-xs-49/simpledemo/cgi-bin/cities-postgresql/wfs"
								+ "?request=GetCapabilities&service=wfs"),
				TestObjectTypeDetectorManager.getTypes("bc6384f3-2652-4c7b-bc45-20cec488ecd0").keySet());
		assertNotNull(detectedType);
		assertEquals("bc6384f3-2652-4c7b-bc45-20cec488ecd0", detectedType.getId().toString());
		assertEquals("db12feeb-0086-4006-bc74-28f4fdef0171", detectedType.getParent().getId().toString());

		final TestObjectDto testObject = new TestObjectDto();
		testObject.addResource(new ResourceDto("serviceEndpoint", "http://none"));
		detectedType.enrichAndNormalize(testObject);

		assertEquals("SimpleDemo WFS", testObject.getLabel());
		assertEquals("SimpleDemo WFS by XtraServer", testObject.getDescription());
		assertEquals("https://services.interactive-instruments.de/cite-xs-49/simpledemo/cgi-bin/cities-postgresql/wfs"
				+ "?request=GetCapabilities&service=wfs&VERSION=1.1.0", testObject.getResourceByName("serviceEndpoint").toString());
	}

	@Test
	public void testWfs20SelectHighest() throws URISyntaxException, IOException, TestObjectTypeNotDetected,
			ObjectWithIdNotFoundException, IncompatibleTestObjectTypeException {
		final DetectedTestObjectType detectedType = TestObjectTypeDetectorManager.detect(
				new StdResource("test",
						"https://services.interactive-instruments.de/cite-xs-49/simpledemo/cgi-bin/cities-postgresql/wfs"
								+ "?request=GetCapabilities&service=wfs"),
				TestObjectTypeDetectorManager.getTypes("db12feeb-0086-4006-bc74-28f4fdef0171").keySet());
		assertNotNull(detectedType);
		assertEquals("9b6ef734-981e-4d60-aa81-d6730a1c6389", detectedType.getId().toString());
		assertEquals("db12feeb-0086-4006-bc74-28f4fdef0171", detectedType.getParent().getId().toString());

		final TestObjectDto testObject = new TestObjectDto();
		testObject.addResource(new ResourceDto("serviceEndpoint", "http://none"));
		detectedType.enrichAndNormalize(testObject);

		assertEquals("SimpleDemo WFS", testObject.getLabel());
		assertEquals("SimpleDemo WFS by XtraServer", testObject.getDescription());
		assertEquals("https://services.interactive-instruments.de/cite-xs-49/simpledemo/cgi-bin/cities-postgresql/wfs"
				+ "?ACCEPTVERSIONS=2.0.0&request=GetCapabilities&service=wfs", testObject.getResourceByName("serviceEndpoint").toString());
	}

	@Test
	public void testIncompleteWfs20Url() throws URISyntaxException, IOException, TestObjectTypeNotDetected,
			ObjectWithIdNotFoundException, IncompatibleTestObjectTypeException {
		// request with version=2.0.0 parameter instead of acceptversions
		final DetectedTestObjectType detectedType = TestObjectTypeDetectorManager.detect(
				new StdResource("test",
						"https://services.interactive-instruments.de/cite-xs-49/simpledemo/cgi-bin/cities-postgresql/wfs"
								+ "?request=GetCapabilities&service=wfs&version=2.0.0"));
		assertNotNull(detectedType);
		assertEquals("9b6ef734-981e-4d60-aa81-d6730a1c6389", detectedType.getId().toString());
		assertEquals("db12feeb-0086-4006-bc74-28f4fdef0171", detectedType.getParent().getId().toString());

		final TestObjectDto testObject = new TestObjectDto();
		testObject.addResource(new ResourceDto("serviceEndpoint", "http://none"));
		detectedType.enrichAndNormalize(testObject);

		assertEquals("SimpleDemo WFS", testObject.getLabel());
		assertEquals("SimpleDemo WFS by XtraServer", testObject.getDescription());
		assertEquals("https://services.interactive-instruments.de/cite-xs-49/simpledemo/cgi-bin/cities-postgresql/wfs"
				+ "?ACCEPTVERSIONS=2.0.0&request=GetCapabilities&service=wfs&version=2.0.0", testObject.getResourceByName("serviceEndpoint").toString());
	}

	public void testIncompatibleTypes() throws URISyntaxException, IOException, TestObjectTypeNotDetected,
			ObjectWithIdNotFoundException, IncompatibleTestObjectTypeException {
		try {
			final DetectedTestObjectType detectedType = TestObjectTypeDetectorManager.detect(
					new StdResource("test",
							"https://services.interactive-instruments.de/cite-xs-49/simpledemo/cgi-bin/cities-postgresql/wfs"
									+ "?request=GetCapabilities&service=wfs&version=2.0.0"),
					// Expect WMS
					TestObjectTypeDetectorManager.getTypes("d1836a8d-9909-4899-a0bc-67f512f5f5ac").keySet());
		}catch (IncompatibleTestObjectTypeException e) {
			assertEquals("9b6ef734-981e-4d60-aa81-d6730a1c6389",
					e.getDetectedTestObjectType().getId().getId());
			return;
		}
		fail("Exception expected");
	}

	@Test(expected = TestObjectTypeNotDetected.class)
	public void testUnknown() throws URISyntaxException, IOException, TestObjectTypeNotDetected, ObjectWithIdNotFoundException, IncompatibleTestObjectTypeException {
		final DetectedTestObjectType detectedType = TestObjectTypeDetectorManager.detect(
				new StdResource("test",
						"http://www.interactive-instruments.de"));
	}

	@Test
	public void testCache() throws URISyntaxException, IOException, TestObjectTypeNotDetected, ObjectWithIdNotFoundException, IncompatibleTestObjectTypeException {
		testWfs20();
		testWfs20();
		testWfs11();
		testWfs20SelectHighest();
		testWfs20SelectHighest();
		boolean exceptionThrown=false;
		try {
			testUnknown();
		}catch(TestObjectTypeNotDetected e) {
			exceptionThrown=true;
		}
		assertTrue(exceptionThrown);
		testWfs11();
		testWfs20();
	}

	@Test
	public void testFile1() throws URISyntaxException, IOException, TestObjectTypeNotDetected {
		final IFile tmpDir = IFile.createTempDir("etf_junit");
		final IFile file = UriUtils.download(new URI("https://3d.bk.tudelft.nl/download/3dfier/Delft.gml.zip"));
		file.unzipTo(tmpDir);
		final DetectedTestObjectType detectedType = TestObjectTypeDetectorManager.detect(
				new StdResource("dir",tmpDir.toURI()));
		assertNotNull(detectedType);
		assertEquals("3e3639b1-f6b7-4d62-9160-963cfb2ea300", detectedType.getId().toString());
	}

	@Test
	public void testFile2() throws URISyntaxException, IOException, TestObjectTypeNotDetected {
		final IFile tmpDir = IFile.createTempDir("etf_junit");
		final IFile file = UriUtils.download(new URI("https://www.dropbox.com/s/uewjg48vq4owwlb/ps-ro-50.zip?dl=1"));
		file.unzipTo(tmpDir);
		final DetectedTestObjectType detectedType = TestObjectTypeDetectorManager.detect(
				new StdResource("dir",tmpDir.toURI()));
		assertNotNull(detectedType);
		assertEquals("a8a1b437-0ebf-454c-8204-bcf0b8548d8c", detectedType.getId().toString());
	}

	@Test
	public void testFile2WithExpectedType() throws URISyntaxException, IOException, TestObjectTypeNotDetected,
			ObjectWithIdNotFoundException, IncompatibleTestObjectTypeException {
		final IFile tmpDir = IFile.createTempDir("etf_junit");
		final IFile file = UriUtils.download(new URI("https://www.dropbox.com/s/uewjg48vq4owwlb/ps-ro-50.zip?dl=1"));
		file.unzipTo(tmpDir);
		final DetectedTestObjectType detectedType = TestObjectTypeDetectorManager.detect(
				new StdResource("dir",tmpDir.toURI()),
				TestObjectTypeDetectorManager.getTypes("a8a1b437-0ebf-454c-8204-bcf0b8548d8c").keySet());
		assertNotNull(detectedType);
		assertEquals("a8a1b437-0ebf-454c-8204-bcf0b8548d8c", detectedType.getId().toString());
	}

}
