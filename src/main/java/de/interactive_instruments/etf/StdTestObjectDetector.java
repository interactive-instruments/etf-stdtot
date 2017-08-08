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

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.*;

import javax.xml.xpath.XPathException;
import javax.xml.xpath.XPathExpressionException;

import jlibs.xml.DefaultNamespaceContext;
import jlibs.xml.sax.dog.DataType;
import jlibs.xml.sax.dog.NodeItem;
import jlibs.xml.sax.dog.XMLDog;
import jlibs.xml.sax.dog.XPathResults;
import jlibs.xml.sax.dog.expr.Expression;

import org.jaxen.saxpath.SAXPathException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;

import de.interactive_instruments.*;
import de.interactive_instruments.etf.dal.dto.capabilities.TestObjectTypeDto;
import de.interactive_instruments.etf.detector.DetectedTestObjectType;
import de.interactive_instruments.etf.detector.TestObjectTypeDetector;
import de.interactive_instruments.etf.model.*;
import de.interactive_instruments.etf.model.capabilities.TestObjectType;
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

	private final List<CompiledXpathExpression> detectionExpressions = new ArrayList<>();

	private final EidMap<CompiledXpathExpression> detectionExpressionsEidMap = new DefaultEidMap<>();

	private final XMLDog xmlDog = new XMLDog(new DefaultNamespaceContext(), null, null);

	private static final class CompiledXpathExpression implements Comparable<CompiledXpathExpression> {
		private final TestObjectTypeDto testObjectType;
		private final Expression detectionExpression;
		private final Expression labelExpression;
		private final Expression descriptionExpression;
		private final int prio;

		private CompiledXpathExpression(final TestObjectTypeDto testObjectType, final XMLDog dog) throws SAXPathException {
			this.testObjectType = testObjectType;

			detectionExpression = dog.addXPath(testObjectType.getDetectionExpression());
			if (detectionExpression.resultType != DataType.BOOLEAN) {
				throw new SAXPathException("Detection expression return type must be boolean");
			}
			if (!SUtils.isNullOrEmpty(testObjectType.getLabelExpression())) {
				labelExpression = dog.addXPath(testObjectType.getLabelExpression());
			} else {
				labelExpression = null;
			}
			if (!SUtils.isNullOrEmpty(testObjectType.getDescriptionExpression())) {
				descriptionExpression = dog.addXPath(testObjectType.getDescriptionExpression());
			} else {
				descriptionExpression = null;
			}

			// order objects with parents before objects without parents
			TestObjectTypeDto parent = this.testObjectType.getParent();
			int cmp = 0;
			for (; parent != null; --cmp) {
				parent = parent.getParent();
			}
			cmp += this.testObjectType.getSubTypes() == null ? -1 : 0;
			prio = cmp;
		}

		private String getValue(final XPathResults results, final Expression expression) {
			if (descriptionExpression != null) {
				final Collection result = (Collection) results.getResult(expression);
				if (result != null && !result.isEmpty()) {
					return ((NodeItem) result.iterator().next()).value;
				}
			}
			return null;
		}

		private DetectedTestObjectType getDetectedTestObjectType(final XPathResults results) throws XPathExpressionException {
			// All expressions are expected to be boolean
			final Object detected = results.getResult(detectionExpression);
			if (detected != null && ((Boolean) detected)) {
				return new StdDetectedTestObjectType(
						this.testObjectType,
						getValue(results, labelExpression),
						getValue(results, descriptionExpression), prio);
			}
			return null;
		}

		@Override
		public int compareTo(final CompiledXpathExpression o) {
			return Integer.compare(this.prio, o.prio);
		}
	}

	private static class StdDetectedTestObjectType implements DetectedTestObjectType {

		private final TestObjectTypeDto testObjectType;
		private final String extractedLabel;
		private final String extractedDescription;
		private final int prio;

		private StdDetectedTestObjectType(final TestObjectTypeDto testObjectType, final String label,
				final String description) {
			this(testObjectType, label, description, 0);
		}

		private StdDetectedTestObjectType(final TestObjectTypeDto testObjectType, final String label, final String description,
				final int prio) {
			this.testObjectType = testObjectType;
			this.extractedLabel = label;
			this.extractedDescription = description;
			this.prio = prio;
		}

		@Override
		public int hashCode() {
			return testObjectType.hashCode();
		}

		@Override
		public boolean equals(final Object obj) {
			return testObjectType.equals(obj);
		}

		@Override
		public List<TestObjectTypeDto> getSubTypes() {
			return testObjectType.getSubTypes();
		}

		@Override
		public List<String> getFilenameExtensions() {
			return testObjectType.getFilenameExtensions();
		}

		@Override
		public List<String> getMimeTypes() {
			return testObjectType.getMimeTypes();
		}

		@Override
		public String getDetectionExpression() {
			return testObjectType.getDetectionExpression();
		}

		@Override
		public ExpressionType getDetectionExpressionType() {
			return testObjectType.getDetectionExpressionType();
		}

		@Override
		public String getLabelExpression() {
			return testObjectType.getLabelExpression();
		}

		@Override
		public ExpressionType getLabelExpressionType() {
			return testObjectType.getLabelExpressionType();
		}

		@Override
		public String getDescriptionExpression() {
			return testObjectType.getDescriptionExpression();
		}

		@Override
		public ExpressionType getDescriptionExpressionType() {
			return testObjectType.getDescriptionExpressionType();
		}

		@Override
		public String getLabel() {
			return testObjectType.getLabel();
		}

		@Override
		public String getDescription() {
			return testObjectType.getDescription();
		}

		@Override
		public EID getId() {
			return testObjectType.getId();
		}

		@Override
		public TestObjectType getParent() {
			return testObjectType.getParent();
		}

		@Override
		public String getExtractedLabel() {
			return extractedLabel;
		}

		@Override
		public String getExtractedDescription() {
			return extractedDescription;
		}

		@Override
		public int compareTo(final Object o) {
			if (o instanceof StdDetectedTestObjectType) {
				return Integer.compare(prio, ((StdDetectedTestObjectType) (o)).prio);
			} else if (o instanceof EidHolder) {
				return getId().compareTo(((EidHolder) (o)).getId());
			}
			throw new IllegalArgumentException("Invalid object type comparison: " +
					o.getClass().getName() + " can not be compared.");
		}

		@Override
		public TestObjectTypeDto toTestObjectTypeDto() {
			return testObjectType.createCopy();
		}
	}

	private DetectedTestObjectType detect(final XPathResults results, final String uriScheme) {
		for (final CompiledXpathExpression detectionExpression : detectionExpressions) {
			try {
				final DetectedTestObjectType type = detectionExpression.getDetectedTestObjectType(results);
				if (type != null) {
					return type;
				}
			} catch (ClassCastException | XPathExpressionException e) {
				logger.error("Could not evaluate XPath expression: ", e);
			}
		}
		// Fallback
		if (uriScheme.startsWith("http")) {
			return new StdDetectedTestObjectType(StdTestObjectTypes.WEB_SERVICE_TOT, null, null);
		} else {
			return new StdDetectedTestObjectType(StdTestObjectTypes.XML_DOCUMENTS_TOT, null, null);
		}
	}

	@Override
	public DetectedTestObjectType detect(final URI uri, final Credentials credentials, final byte[] cachedContent) {
		try {
			xmlDog.sniff(new InputSource(new ByteArrayInputStream(cachedContent)));
		} catch (final XPathException e) {
			logger.error("Error occurred during Test Object Type detection {} :", uri.getPath(), e);
		}
		return null;
	}

	private DetectedTestObjectType detectFromSamples(final URI directory) throws IOException {
		final IFile dir = new IFile(directory);
		final List<IFile> files = dir.getFilesInDirRecursive(GmlAndXmlFilter.instance().filename(), 6, false);
		if (files == null || files.size() == 0) {
			throw new IOException("No files found");
		}
		final List<DetectedTestObjectType> detectedTypes = new ArrayList<>(5);
		for (final IFile sample : Sample.normalDistributed(files, 5)) {
			try {
				final InputStream inputStream = new FileInputStream(sample);
				detectedTypes.add(detect(xmlDog.sniff(new InputSource(inputStream)), "file"));
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

	@Override
	public DetectedTestObjectType detect(final URI uri, final Credentials credentials) {
		try {
			if (!UriUtils.isFile(uri) || !new IFile(uri).isDirectory()) {
				final UriUtils.HttpInputStream inputStream = (UriUtils.HttpInputStream) UriUtils.openStream(uri, credentials);
				return detect(xmlDog.sniff(new InputSource(inputStream), true), uri.getScheme());
			} else {
				return detectFromSamples(uri);
			}
		} catch (IOException | XPathException e) {
			logger.error("Error occurred during Test Object Type detection {} :", uri.getPath(), e);
		}
		return null;
	}

	@Override
	public DetectedTestObjectType detectType(final EID testObjectTypeId, final URI uri, final Credentials credentials) {
		final CompiledXpathExpression detectionExpression = detectionExpressionsEidMap.get(testObjectTypeId);
		if (detectionExpression == null) {
			return null;
		}
		try {
			if (!UriUtils.isFile(uri) || !new IFile(uri).isDirectory()) {
				final UriUtils.HttpInputStream inputStream = (UriUtils.HttpInputStream) UriUtils.openStream(uri, credentials);
				return detectionExpression.getDetectedTestObjectType(xmlDog.sniff(new InputSource(inputStream)));
			} else {
				return detectFromSamples(uri);
			}
		} catch (IOException | XPathException e) {
			logger.error("Error occurred during Test Object Type detection ", e);
		}
		return null;
	}

	@Override
	public EidMap<TestObjectTypeDto> supportedTypes() {
		return StdTestObjectTypes.types;
	}

	@Override
	public void init() throws ConfigurationException, InitializationException, InvalidStateTransitionException {
		for (final TestObjectTypeDto testObjectType : StdTestObjectTypes.types.values()) {
			if (!SUtils.isNullOrEmpty(testObjectType.getDetectionExpression())) {
				try {
					final CompiledXpathExpression compiledExpression = new CompiledXpathExpression(testObjectType, this.xmlDog);
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
}
