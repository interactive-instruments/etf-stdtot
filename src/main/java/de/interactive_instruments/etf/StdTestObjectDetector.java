/**
 * Copyright 2017-2018 European Union
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 *
 * This work was supported by the EU Interoperability Solutions for
 * European Public Administrations Programme (http://ec.europa.eu/isa)
 * through Action 1.17: A Reusable INSPIRE Reference Platform (ARE3NA).
 */
package de.interactive_instruments.etf;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

import javax.xml.xpath.XPathException;
import javax.xml.xpath.XPathExpressionException;

import jlibs.xml.DefaultNamespaceContext;
import jlibs.xml.sax.dog.XMLDog;
import jlibs.xml.sax.dog.XPathResults;

import org.jaxen.saxpath.SAXPathException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;

import de.interactive_instruments.*;
import de.interactive_instruments.etf.dal.dto.capabilities.TestObjectTypeDto;
import de.interactive_instruments.etf.detector.DetectedTestObjectType;
import de.interactive_instruments.etf.detector.TestObjectTypeDetector;
import de.interactive_instruments.etf.model.DefaultEidMap;
import de.interactive_instruments.etf.model.EID;
import de.interactive_instruments.etf.model.EidMap;
import de.interactive_instruments.etf.model.capabilities.CachedRemoteResource;
import de.interactive_instruments.etf.model.capabilities.LocalResource;
import de.interactive_instruments.etf.model.capabilities.RemoteResource;
import de.interactive_instruments.etf.model.capabilities.Resource;
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
		for (final TestObjectTypeDto testObjectType : supportedTypes().values()) {
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

	private DetectedTestObjectType detectLocalFile(final XPathResults results,
			final LocalResource resource, final List<CompiledDetectionExpression> expressions) {
		for (final CompiledDetectionExpression detectionExpression : expressions) {
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
		return null;
	}

	/**
	 * Detect Test Object Type from samples in a directory
	 *
	 * @param localResource directory as URI
	 * @return Test Object Type or null if unknown
	 * @throws IOException if an error occurs accessing the files
	 */
	private DetectedTestObjectType detectInLocalDirFromSamples(final List<CompiledDetectionExpression> expressions,
			final LocalResource localResource) throws IOException {
		final IFile dir = localResource.getFile();
		final List<IFile> files = dir.getFilesInDirRecursive(GmlAndXmlFilter.instance().filename(), 6, false);
		if (files == null || files.size() == 0) {
			return null;
		}
		final TreeSet<DetectedTestObjectType> detectedTypes = new TreeSet<>();
		for (final IFile sample : Sample.normalDistributed(files, 7)) {
			try {
				final InputStream inputStream = new FileInputStream(sample);
				final DetectedTestObjectType detectedType = detectLocalFile(xmlDog.sniff(new InputSource(inputStream)),
						localResource, expressions);
				if (detectedType != null) {
					detectedTypes.add(detectedType);
				}
				if (detectedTypes.size() >= expressions.size()) {
					// skip if we have detected types for all expressions
					break;
				}
			} catch (XPathException e) {
				ExcUtils.suppress(e);
			}
		}
		if (detectedTypes.isEmpty()) {
			return null;
		}
		return detectedTypes.first();
	}

	/**
	 *
	 * @param detectionExpression
	 * @param resource
	 * @return
	 */
	private DetectedTestObjectType detectRemote(final CompiledDetectionExpression detectionExpression,
			final CachedRemoteResource resource) {
		try {
			//
			final Resource normalizedResource = detectionExpression.getNormalizedResource(resource);
			return detectionExpression.getDetectedTestObjectType(
					xmlDog.sniff(new InputSource(normalizedResource.openStream())), normalizedResource);
		} catch (IOException | XPathException e) {
			logger.error("Error occurred during Test Object Type detection ", e);
		}
		return null;
	}

	private DetectedTestObjectType detectedType(final Resource resource,
			final List<CompiledDetectionExpression> expressions) {
		Collections.sort(expressions);

		// detect remote type
		if (resource instanceof RemoteResource) {
			final CachedRemoteResource cachedResource = Resource.toCached((RemoteResource) resource);
			for (final CompiledDetectionExpression expression : expressions) {
				final DetectedTestObjectType detectedType = detectRemote(expression, cachedResource);
				if (detectedType != null) {
					return detectedType;
				}
			}
		} else {
			try {
				return detectInLocalDirFromSamples(expressions, (LocalResource) resource);
			} catch (IOException ign) {
				ExcUtils.suppress(ign);
				return null;
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
			}
		}
		if (!uriDetectionCandidates.isEmpty()) {
			// Test Object types could be detected by URI
			final DetectedTestObjectType detectedType = detectedType(resource, uriDetectionCandidates);
			if (detectedType != null) {
				return detectedType;
			}
		}
		// Test Object types could not be identified by URI
		final DetectedTestObjectType detectedType = detectedType(resource, expressionsForExpectedTypes);
		if (detectedType != null) {
			return detectedType;
		}

		// should never happen, fallback types are XML and WEBSERVICE
		return null;
	}

	@Override
	public DetectedTestObjectType detectType(final Resource resource) {
		return detectedType(resource, detectionExpressions);
	}
}
