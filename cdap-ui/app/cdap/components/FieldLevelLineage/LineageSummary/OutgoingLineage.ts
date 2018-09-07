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

import LineageSummary from 'components/FieldLevelLineage/LineageSummary';
import { connect } from 'react-redux';

const mapStateToProps = (state) => {
  return {
    activeField: state.lineage.activeField,
    datasetId: state.lineage.datasetId,
    summary: state.lineage.incoming,
    direction: 'outgoing',
  };
};

const OutgoingLineage = connect(
  mapStateToProps,
)(LineageSummary);

export default OutgoingLineage;
