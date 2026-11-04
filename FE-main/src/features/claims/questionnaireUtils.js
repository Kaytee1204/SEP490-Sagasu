export function isQuestionVisible(question, answers, definitions, visiting = new Set()) {
  if (!question.visibility) return true;
  if (visiting.has(question.code)) return false;
  visiting.add(question.code);
  try {
    return question.visibility.anyOf.some(condition => {
      const parent = definitions[condition.questionCode];
      const answer = answers[condition.questionCode];
      const selected = typeof answer === 'string' ? answer : answer?.choice;
      return parent && isQuestionVisible(parent, answers, definitions, visiting) && condition.values.includes(selected);
    });
  } finally {
    visiting.delete(question.code);
  }
}

export function questionnaireQuestions(questionnaire) {
  return [...questionnaire.commonQuestions, ...questionnaire.categoryQuestions];
}

export function serializeQuestionnaireAnswers(questionnaire, answers) {
  const questions = questionnaireQuestions(questionnaire);
  const definitions = Object.fromEntries(questions.map(question => [question.code, question]));
  return questions.filter(question => !['DESCRIPTION', 'EVIDENCE'].includes(question.inputType)
    && isQuestionVisible(question, answers, definitions))
    .filter(question => {
      const value = answers[question.code];
      return typeof value === 'string' ? Boolean(value.trim()) : Boolean(value?.choice || value?.precision);
    }).map(question => ({ questionCode: question.code, value: answers[question.code] }));
}
