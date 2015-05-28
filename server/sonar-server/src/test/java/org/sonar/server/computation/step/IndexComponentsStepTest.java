/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2014 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.sonar.server.computation.step;

import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.batch.protocol.output.BatchReport;
import org.sonar.core.component.ComponentDto;
import org.sonar.core.resource.ResourceIndexerDao;
import org.sonar.server.computation.ComputationContext;
import org.sonar.server.computation.batch.BatchReportReaderRule;
import org.sonar.server.computation.component.ComponentTreeBuilders;
import org.sonar.server.computation.component.DbComponentsRefCache;
import org.sonar.server.computation.component.DbComponentsRefCache.DbComponent;
import org.sonar.server.computation.component.DumbComponent;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IndexComponentsStepTest extends BaseStepTest {

  private static final String PROJECT_KEY = "PROJECT_KEY";

  @Rule
  public BatchReportReaderRule reportReader = new BatchReportReaderRule();

  ResourceIndexerDao resourceIndexerDao = mock(ResourceIndexerDao.class);
  DbComponentsRefCache dbComponentsRefCache = new DbComponentsRefCache();
  IndexComponentsStep sut = new IndexComponentsStep(resourceIndexerDao, dbComponentsRefCache, reportReader);

  @Test
  public void call_indexProject_of_dao() throws IOException {
    dbComponentsRefCache.addComponent(1, new DbComponent(123L, PROJECT_KEY, "PROJECT_UUID"));

    reportReader.setMetadata(BatchReport.Metadata.newBuilder()
      .setRootComponentRef(1)
      .build());

    ComponentDto project = mock(ComponentDto.class);
    when(project.getId()).thenReturn(123L);
    ComputationContext context = new ComputationContext(ComponentTreeBuilders.from(DumbComponent.DUMB_PROJECT));

    sut.execute(context);

    verify(resourceIndexerDao).indexProject(123L);
  }

  @Override
  protected ComputationStep step() {
    return sut;
  }
}
