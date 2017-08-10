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

import de.interactive_instruments.SUtils;
import de.interactive_instruments.UriUtils;
import de.interactive_instruments.etf.dal.dto.capabilities.TestObjectTypeDto;
import de.interactive_instruments.etf.detector.DetectedTestObjectType;
import de.interactive_instruments.etf.model.capabilities.MutableCachedResource;
import de.interactive_instruments.etf.model.capabilities.Resource;
import jlibs.xml.sax.dog.DataType;
import jlibs.xml.sax.dog.NodeItem;
import jlibs.xml.sax.dog.XMLDog;
import jlibs.xml.sax.dog.XPathResults;
import jlibs.xml.sax.dog.expr.Expression;
import org.jaxen.saxpath.SAXPathException;

import javax.xml.xpath.XPathExpressionException;
import java.net.URI;
import java.util.Collection;
import java.util.regex.Pattern;

/**
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
final class CompiledDetectionExpression implements Comparable<CompiledDetectionExpression> {
	private final TestObjectTypeDto testObjectType;
	private final Expression detectionExpression;
	private final Expression labelExpression;
	private final Expression descriptionExpression;
	private final int priority;
	private final Pattern uriDetectionPattern;

	CompiledDetectionExpression(final TestObjectTypeDto testObjectType, final XMLDog dog)
			throws SAXPathException {
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
		if (!SUtils.isNullOrEmpty(testObjectType.getUriDetectionExpression())) {
			uriDetectionPattern = Pattern.compile(testObjectType.getUriDetectionExpression(),
					Pattern.CASE_INSENSITIVE);
		} else {
			uriDetectionPattern = null;
		}

		// order objects with parents before objects without parents
		TestObjectTypeDto parent = this.testObjectType.getParent();
		int cmp = 0;
		for (; parent != null; --cmp) {
			parent = parent.getParent();
		}
		cmp += this.testObjectType.getSubTypes() == null ? -1 : 0;
		priority = cmp;
	}

	String getValue(final XPathResults results, final Expression expression) {
		if (descriptionExpression != null) {
			final Collection result = (Collection) results.getResult(expression);
			if (result != null && !result.isEmpty()) {
				return ((NodeItem) result.iterator().next()).value;
			}
		}
		return null;
	}

	DetectedTestObjectType getDetectedTestObjectType(
			final XPathResults results, final Resource normalizedResource)
			throws XPathExpressionException {
		// All expressions are expected to be boolean
		final Object detected = results.getResult(detectionExpression);
		if (detected != null && ((Boolean) detected)) {
			return new StdDetectedTestObjectType(
					this.testObjectType,
					normalizedResource,
					getValue(results, labelExpression),
					getValue(results, descriptionExpression), priority);
		}
		return null;
	}

	boolean isUriKnown(final URI uri) {
		if (uriDetectionPattern != null) {
			return uriDetectionPattern.matcher(uri.toString()).matches();
		}
		return false;
	}

	Resource getNormalizedResource(final Resource resource) {
		if (this.testObjectType.getDefaultQuery() != null) {
			final MutableCachedResource normalizedResource = new MutableCachedResource(resource);
			normalizedResource.setQueyParameters(
					UriUtils.toSingleQueryParameterValues(this.testObjectType.getDefaultQuery()));
			return normalizedResource;
		}
		return resource;
	}

	@Override
	public int compareTo(final CompiledDetectionExpression o) {
		final int cmp = Integer.compare(this.priority, o.priority);
		if(cmp==0) {
			// Compare label so that "OGC Web Feature Service 2.0" is tested before
			// "OGC Web Feature Service 1.1"
			return this.testObjectType.getLabel().compareTo(o.testObjectType.getLabel());
		}
		return cmp;
	}
}
