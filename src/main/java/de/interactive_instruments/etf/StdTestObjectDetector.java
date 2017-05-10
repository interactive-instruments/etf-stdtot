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

import java.net.URI;
import java.util.*;

import de.interactive_instruments.Credentials;
import de.interactive_instruments.etf.dal.dto.capabilities.TestObjectTypeDto;
import de.interactive_instruments.etf.detector.TestObjectTypeDetector;
import de.interactive_instruments.etf.model.DefaultEidMap;
import de.interactive_instruments.etf.model.EID;
import de.interactive_instruments.etf.model.EidFactory;
import de.interactive_instruments.etf.model.EidMap;
import de.interactive_instruments.etf.model.capabilities.TestObjectType;

public class StdTestObjectDetector implements TestObjectTypeDetector {

	private static final TestObjectTypeDto INSPIRE_DATA_SET_IN_GML_TOT = new TestObjectTypeDto();
	static final EID INSPIRE_DATA_SET_IN_GML_ID = EidFactory.getDefault()
			.createAndPreserveStr("e1d4a306-7a78-4a3b-ae2d-cf5f0810853e");

	// Supported Test Object Types
	private static final TestObjectTypeDto SIMPLE_WEB_SERVICE_TOT = new TestObjectTypeDto();
	static EID SIMPLE_WEB_SERVICE_ID = EidFactory.getDefault().createAndPreserveStr("88311f83-818c-46ed-8a9a-cec4f3707365");

	private static final TestObjectTypeDto WFS_2_0_TOT = new TestObjectTypeDto();
	static EID WFS_2_0_ID = EidFactory.getDefault().createAndPreserveStr("9b6ef734-981e-4d60-aa81-d6730a1c6389");

	private static final TestObjectTypeDto INSPIRE_DOWNLOAD_DIRECT_TOT = new TestObjectTypeDto();
	static EID INSPIRE_DOWNLOAD_DIRECT_ID = EidFactory.getDefault()
			.createAndPreserveStr("dd83316d-7c30-49ad-8cc9-e5b73e501faa");

	private static final TestObjectTypeDto ATOM_TOT = new TestObjectTypeDto();
	static EID ATOM_ID = EidFactory.getDefault().createAndPreserveStr("49d881ae-b115-4b91-aabe-31d5791bce52");

	private static final TestObjectTypeDto INSPIRE_DOWNLOAD_ATOM_TOT = new TestObjectTypeDto();
	static EID INSPIRE_DOWNLOAD_ATOM_ID = EidFactory.getDefault().createAndPreserveStr("9c5bd10c-8311-4f0b-9633-b867028cacd6");

	// Supported Test Object Types
	private final static EidMap<TestObjectTypeDto> types = new DefaultEidMap<>(
			Collections.unmodifiableMap(new LinkedHashMap<EID, TestObjectTypeDto>() {
				{
					INSPIRE_DATA_SET_IN_GML_TOT.setLabel("INSPIRE data set in GML");
					INSPIRE_DATA_SET_IN_GML_TOT.setId(INSPIRE_DATA_SET_IN_GML_ID);
					INSPIRE_DATA_SET_IN_GML_TOT.setDescription("A set of XML documents. "
							+ "Each document is either a WFS 2.0 FeatureCollection, a GML 3.2 Feature Collection "
							+ "or an INSPIRE Base 3.2 or 3.3 SpatialDataSet. All features are GML 3.2 Features.");
					put(INSPIRE_DATA_SET_IN_GML_TOT.getId(), INSPIRE_DATA_SET_IN_GML_TOT);
				}

				{
					SIMPLE_WEB_SERVICE_TOT.setLabel("Web Service");
					SIMPLE_WEB_SERVICE_TOT.setId(SIMPLE_WEB_SERVICE_ID);
					SIMPLE_WEB_SERVICE_TOT
							.setDescription("A web Service with a HTTP interface that is not described in more detail.");
					put(SIMPLE_WEB_SERVICE_ID, SIMPLE_WEB_SERVICE_TOT);
				}

				{
					WFS_2_0_TOT.setLabel("Web Feature Service 2.0");
					WFS_2_0_TOT.setId(WFS_2_0_ID);
					WFS_2_0_TOT.setDescription(
							"A Web Service implementing ISO 19142 Web Feature Service and ISO 19143 Filter Encoding");
					WFS_2_0_TOT.setParent(SIMPLE_WEB_SERVICE_TOT);
					put(WFS_2_0_ID, WFS_2_0_TOT);
				}

				{
					INSPIRE_DOWNLOAD_DIRECT_TOT.setLabel("INSPIRE Download Service 3.1 - Direct Access WFS 2.0");
					INSPIRE_DOWNLOAD_DIRECT_TOT.setId(INSPIRE_DOWNLOAD_DIRECT_ID);
					INSPIRE_DOWNLOAD_DIRECT_TOT.setDescription("INSPIRE Download Service 3.1 - Direct Access WFS 2.0");
					INSPIRE_DOWNLOAD_DIRECT_TOT.setParent(WFS_2_0_TOT);
					put(INSPIRE_DOWNLOAD_DIRECT_ID, INSPIRE_DOWNLOAD_DIRECT_TOT);
				}

				{
					ATOM_TOT.setLabel("Web Feed");
					ATOM_TOT.setId(ATOM_ID);
					ATOM_TOT.setDescription(
							"A Web Feed implementing the Atom Syndication Format, describing update information about published data.");
					put(ATOM_ID, ATOM_TOT);
				}

				{
					INSPIRE_DOWNLOAD_ATOM_TOT.setLabel("INSPIRE Download Service 3.1 - Download Service Feed");
					INSPIRE_DOWNLOAD_ATOM_TOT.setId(INSPIRE_DOWNLOAD_ATOM_ID);
					INSPIRE_DOWNLOAD_ATOM_TOT.setDescription(
							"A top-level Download Service Feed, describing update information about published pre-defined datasets.");
					INSPIRE_DOWNLOAD_ATOM_TOT.setParent(ATOM_TOT);
					put(INSPIRE_DOWNLOAD_ATOM_ID, INSPIRE_DOWNLOAD_ATOM_TOT);
				}
			}));

	@Override
	public TestObjectType detect(final byte[] bytes) {
		return null;
	}

	@Override
	public TestObjectType detect(final URI uri, final Credentials credentials) {
		return null;
	}

	@Override
	public EidMap<TestObjectTypeDto> supportedTypes() {
		return types;
	}
}
