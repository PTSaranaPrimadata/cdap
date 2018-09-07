/*
 * Copyright © 2018 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

import React from 'react';
import PropTypes from 'prop-types';

import SummaryRow from 'components/FieldLevelLineage/LineageSummary/SummaryRow';
import {getOperations} from 'components/FieldLevelLineage/store/ActionCreator';
import T from 'i18n-react';

const PREFIX = 'features.FieldLevelLineage.Summary';

require('./LineageSummary.scss');

export default function LineageSummary({activeField, datasetId, summary, direction}) {
  const datasetFieldTitle = `${datasetId}: ${activeField}`;

  return (
    <div className="lineage-summary-container">
      <div className="field-lineage-info">
        <div className="title">
          <strong>
            {T.translate(`${PREFIX}.Title.${direction}`)}
          </strong>

          <span
            className="dataset-field truncate"
            title={datasetFieldTitle}
          >
            {datasetFieldTitle}
          </span>
        </div>

        <div className="lineage-count">
          {T.translate(`${PREFIX}.datasetCount`, { context: summary.length })}
        </div>
      </div>

      <div className="lineage-fields">
        <div className="lineage-column lineage-fields-header">
          <div className="index" />
          <div className="dataset-name">
            Dataset name
        </div>
          <div className="field-name">
            Field name
          </div>
        </div>

        <div className="lineage-fields-body">
          {
            summary.map((entity, i) => {
              return (
                <SummaryRow
                  key={i}
                  entity={entity}
                  index={i}
                />
              );
            })
          }
        </div>
      </div>

      <div className="view-operations">
        <span
          onClick={getOperations.bind(null, direction)}
        >
          {T.translate(`${PREFIX}.viewOperations`)}
        </span>
      </div>
    </div>
  );
}

LineageSummary.propTypes = {
  activeField: PropTypes.string,
  datasetId: PropTypes.string,
  summary: PropTypes.array,
  direction: PropTypes.oneOf(['incoming', 'outgoing'])
};
