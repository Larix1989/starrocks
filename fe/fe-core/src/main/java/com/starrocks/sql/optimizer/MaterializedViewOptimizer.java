// Copyright 2021-present StarRocks, Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.


package com.starrocks.sql.optimizer;

import com.starrocks.catalog.MaterializedView;
import com.starrocks.catalog.MvPlanContext;
import com.starrocks.common.Pair;
import com.starrocks.qe.ConnectContext;
import com.starrocks.sql.optimizer.base.ColumnRefFactory;
import com.starrocks.sql.optimizer.rule.RuleSetType;
import com.starrocks.sql.optimizer.rule.RuleType;
import com.starrocks.sql.optimizer.rule.transformation.materialization.MvUtils;
import com.starrocks.sql.optimizer.transformer.LogicalPlan;

public class MaterializedViewOptimizer {
    public MvPlanContext optimize(MaterializedView mv,
                                  ConnectContext connectContext) {
        // optimize the sql by rule and disable rule based materialized view rewrite
        OptimizerConfig optimizerConfig = new OptimizerConfig(OptimizerConfig.OptimizerAlgorithm.RULE_BASED);
        // Disable partition prune for mv's plan so no needs  to compensate pruned predicates anymore.
        // Only needs to compensate mv's ref-base-table's partition predicates when mv's freshness cannot be satisfied.
        optimizerConfig.disableRuleSet(RuleSetType.PARTITION_PRUNE);
        optimizerConfig.disableRuleSet(RuleSetType.SINGLE_TABLE_MV_REWRITE);
        optimizerConfig.disableRule(RuleType.TF_REWRITE_GROUP_BY_COUNT_DISTINCT);
        optimizerConfig.disableRule(RuleType.TF_REWRITE_SIMPLE_AGG);
        // For sync mv, no rewrite query by original sync mv rule to avoid useless rewrite.
        if (mv.getRefreshScheme().isSync()) {
            optimizerConfig.disableRule(RuleType.TF_MATERIALIZED_VIEW);
        }

        ColumnRefFactory columnRefFactory = new ColumnRefFactory();
        String mvSql = mv.getViewDefineSql();
        Pair<OptExpression, LogicalPlan> plans =
                MvUtils.getRuleOptimizedLogicalPlan(mv, mvSql, columnRefFactory, connectContext, optimizerConfig);
        if (plans == null) {
            return null;
        }
        OptExpression mvPlan = plans.first;
        if (!MvUtils.isValidMVPlan(mvPlan)) {
            return new MvPlanContext();
        }
        MvPlanContext mvRewriteContext =
                new MvPlanContext(mvPlan, plans.second.getOutputColumn(), columnRefFactory);
        return mvRewriteContext;
    }
}
