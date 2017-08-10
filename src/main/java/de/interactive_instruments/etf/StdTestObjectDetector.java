/**
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
package de.interactive_instruments.etf;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.xml.xpath.XPathException;
import javax.xml.xpath.XPathExpressionException;

import jlibs.xml.DefaultNamespaceContext;
import jlibs.xml.sax.dog.XMLDog;
import jlibs.xml.sax.dog.XPathResults;

import org.jaxen.saxpath.SAXPathException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;

import de.interactive_instruments.IFile;
import de.interactive_instruments.SUtils;
import de.interactive_instruments.Sample;
import de.interactive_instruments.UriUtils;
import de.interactive_instruments.etf.dal.dto.capabilities.TestObjectTypeDto;
import de.interactive_instruments.etf.detector.DetectedTestObjectType;
import de.interactive_instruments.etf.detector.TestObjectTypeDetector;
import de.interactive_instruments.etf.model.DefaultEidMap;
import de.interactive_instruments.etf.model.EID;
import de.interactive_instruments.etf.model.EidMap;
import de.interactive_instruments.etf.model.capabilities.Resource;
import de.interactive_instruments.etf.model.capabilities.StdResource;
import de.interactive_instruments.exceptions.ExcUtils;
import de.interactive_instruments.exceptions.InitializationException;
import de.interactive_instruments.exceptions.InvalidStateTransitionException;
import de.interactive_instruments.exceptions.config.ConfigurationException;
import de.interactive_instruments.io.GmlAndXmlFilter;

/**
 * Standard detector for Test Object Types.
 *
 * The standard detector takes xpath expressions for detecting the test object types, and
 * checks for matches in XML files. As the jdk xpath engine is very slow and memory hungry,
 * the XMLDog engine which is based on Sax is used.
 *
 * Note: Only a subset of XPath 1.0 is supported https://github.com/santhosh-tekuri/jlibs/wiki/XMLDog
 *
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 * @author Clemens Portele ( portele aT interactive-instruments doT de )
 */
public class StdTestObjectDetector implements TestObjectTypeDetector {

	private static Logger logger = LoggerFactory.getLogger(StdTestObjectDetector.class);
	private boolean initialized = false;

	private final List<CompiledDetectionExpression> detectionExpressions = new ArrayList<>();

	private final EidMap<CompiledDetectionExpression> detectionExpressionsEidMap = new DefaultEidMap<>();

	private final XMLDog xmlDog = new XMLDog(new DefaultNamespaceContext(), null, null);

	@Override
	public EidMap<TestObjectTypeDto> supportedTypes() {
		return StdTestObjectTypes.types;
	}

	@Override
	public void init() throws ConfigurationException, InitializationException, InvalidStateTransitionException {
		for (final TestObjectTypeDto testObjectType : StdTestObjectTypes.types.values()) {
			if (!SUtils.isNullOrEmpty(testObjectType.getDetectionExpression())) {
				try {
					final CompiledDetectionExpression compiledExpression = new CompiledDetectionExpression(testObjectType,
							this.xmlDog);
					detectionExpressions.add(compiledExpression);
					detectionExpressionsEidMap.put(testObjectType.getId(), compiledExpression);
				} catch (final SAXPathException e) {
					logger.error("Could not compile XPath expression: ", e);
				}
			}
		}
		Collections.sort(detectionExpressions);
		initialized = true;
	}

	@Override
	public boolean isInitialized() {
		return initialized;
	}

	@Override
	public void release() {
		detectionExpressions.clear();
		detectionExpressionsEidMap.clear();
		initialized = false;
	}

	private DetectedTestObjectType detect(final XPathResults results, final Resource resource) {
		for (final CompiledDetectionExpression detectionExpression : detectionExpressions) {
			try {
				final DetectedTestObjectType type = detectionExpression.getDetectedTestObjectType(
						results, resource);
				if (type != null) {
					return type;
				}
			} catch (ClassCastException | XPathExpressionException e) {
				logger.error("Could not evaluate XPath expression: ", e);
			}
		}
		// Fallback
		if (resource.getUri().getScheme().startsWith("http")) {
			return new StdDetectedTestObjectType(StdTestObjectTypes.WEB_SERVICE_TOT, resource);
		} else {
			return new StdDetectedTestObjectType(StdTestObjectTypes.XML_DOCUMENTS_TOT, resource);
		}
	}

	/**
	 * Detect Test Object Type from samples in a directory
	 *
	 * @param directory directory as URI
	 * @return Test Object Type or null if unknown
	 * @throws IOException if an error occurs accessing the files
	 */
	private DetectedTestObjectType detectInDirFromSamples(final URI directory) throws IOException {
		final IFile dir = new IFile(directory);
		final List<IFile> files = dir.getFilesInDirRecursive(GmlAndXmlFilter.instance().filename(), 6, false);
		if (files == null || files.size() == 0) {
			throw new IOException("No files found");
		}
		final List<DetectedTestObjectType> detectedTypes = new ArrayList<>(5);
		for (final IFile sample : Sample.normalDistributed(files, 5)) {
			try {
				final InputStream inputStream = new FileInputStream(sample);
				detectedTypes.add(detect(xmlDog.sniff(new InputSource(inputStream)),
						new StdResource("dir", directory)));
			} catch (XPathException e) {
				ExcUtils.suppress(e);
			}
		}
		if (detectedTypes.size() == 0) {
			return null;
		}
		Collections.sort(detectedTypes);
		return detectedTypes.get(0);
	}

	private DetectedTestObjectType detectType(final CompiledDetectionExpression detectionExpression, final Resource resource) {
		try {
			if (!UriUtils.isFile(resource.getUri()) || !new IFile(resource.getUri()).isDirectory()) {
				final Resource normalizedResource = detectionExpression.getNormalizedResource(resource);
				return detectionExpression.getDetectedTestObjectType(
						xmlDog.sniff(new InputSource(normalizedResource.openStream())), normalizedResource);
			} else {
				return detectInDirFromSamples(resource.getUri());
			}
		} catch (IOException | XPathException e) {
			logger.error("Error occurred during Test Object Type detection ", e);
		}
		return null;
	}

	private DetectedTestObjectType getFirstDetectedType(final Resource resource,
			final List<CompiledDetectionExpression> expressions) {
		Collections.sort(expressions);
		for (final CompiledDetectionExpression expression : expressions) {
			final DetectedTestObjectType detectedType = detectType(expression, resource);
			if (detectedType != null) {
				return detectedType;
			}
		}
		return null;
	}

	@Override
	public DetectedTestObjectType detectType(final Resource resource, final Set<EID> expectedTypes) {

		// Types that can be detected by URI
		final List<CompiledDetectionExpression> uriDetectionCandidates = new ArrayList<>();
		// All others
		final List<CompiledDetectionExpression> expressionsForExpectedTypes = new ArrayList<>();
		for (final EID expectedType : expectedTypes) {
			final CompiledDetectionExpression detectionExpression = detectionExpressionsEidMap.get(expectedType);
			if (detectionExpression != null) {
				if (detectionExpression.isUriKnown(resource.getUri())) {
					uriDetectionCandidates.add(detectionExpression);
				} else {
					expressionsForExpectedTypes.add(detectionExpression);
				}
			} else {

			}
		}
		if (!uriDetectionCandidates.isEmpty()) {
			// Test Object types could be detected by URI
			final DetectedTestObjectType detectedType = getFirstDetectedType(resource, uriDetectionCandidates);
			if (detectedType != null) {
				return detectedType;
			}
		}
		// Test Object types could not be identified by URI
		final DetectedTestObjectType detectedType = getFirstDetectedType(resource, expressionsForExpectedTypes);
		if (detectedType != null) {
			return detectedType;
		}
		return null;
	}

	@Override
	public DetectedTestObjectType detectType(final Resource resource) {
		return getFirstDetectedType(resource, detectionExpressionsEidMap.asList());
	}
}
