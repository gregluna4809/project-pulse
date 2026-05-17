package com.projectpulse.rules;

import com.projectpulse.model.ProjectAnalysis;
import com.projectpulse.model.RuleFinding;
import java.util.Optional;

public interface ProjectRule {

    Optional<RuleFinding> evaluate(ProjectAnalysis analysis);
}
