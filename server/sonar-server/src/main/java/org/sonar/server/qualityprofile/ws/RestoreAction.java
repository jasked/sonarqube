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
package org.sonar.server.qualityprofile.ws;

import com.google.common.base.Preconditions;
import org.apache.commons.io.IOUtils;
import org.sonar.api.resources.Languages;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.text.JsonWriter;
import org.sonar.core.permission.GlobalPermissions;
import org.sonar.core.qualityprofile.db.QualityProfileDto;
import org.sonar.server.qualityprofile.BulkChangeResult;
import org.sonar.server.qualityprofile.QProfileBackuper;
import org.sonar.server.user.UserSession;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class RestoreAction implements QProfileWsAction {

  private static final String PARAM_BACKUP = "backup";
  private final QProfileBackuper backuper;
  private final Languages languages;
  private final UserSession userSession;

  public RestoreAction(QProfileBackuper backuper, Languages languages, UserSession userSession) {
    this.backuper = backuper;
    this.languages = languages;
    this.userSession = userSession;
  }

  @Override
  public void define(WebService.NewController controller) {
    controller.createAction("restore")
      .setSince("5.2")
      .setDescription("Restore a quality profile using an XML file. The restored profile name is taken from the backup file, "
        + "so if a profile with the same name and language already exists, it will be overwritten.")
      .setPost(true)
      .setHandler(this)
      .createParam(PARAM_BACKUP)
      .setDescription("A profile backup file in XML format, as generated by api/qualityprofiles/backup " +
        "or the former api/profiles/backup.")
      .setRequired(true);
  }

  @Override
  public void handle(Request request, Response response) throws Exception {
    userSession.checkLoggedIn().checkGlobalPermission(GlobalPermissions.QUALITY_PROFILE_ADMIN);
    InputStream backup = request.paramAsInputStream(PARAM_BACKUP);
    InputStreamReader reader = null;

    try {
      Preconditions.checkArgument(backup != null, "A backup file must be provided");
      reader = new InputStreamReader(backup, StandardCharsets.UTF_8);
      BulkChangeResult result = backuper.restore(reader, null);
      writeResponse(response.newJsonWriter(), result);
    } finally {
      IOUtils.closeQuietly(reader);
      IOUtils.closeQuietly(backup);
    }
  }

  private void writeResponse(JsonWriter json, BulkChangeResult result) {
    QualityProfileDto profile = result.profile();
    if (profile != null) {
      String language = profile.getLanguage();
      json.beginObject().name("profile").beginObject()
        .prop("key", profile.getKey())
        .prop("name", profile.getName())
        .prop("language", language)
        .prop("languageName", languages.get(profile.getLanguage()).getName())
        .prop("isDefault", false)
        .prop("isInherited", false)
        .endObject();
    }
    json.prop("ruleSuccesses", result.countSucceeded());
    json.prop("ruleFailures", result.countFailed());
    json.endObject().close();
  }
}
