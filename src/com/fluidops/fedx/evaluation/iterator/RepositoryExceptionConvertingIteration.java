/*
 * Copyright (C) 2018 Veritas Technologies LLC.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.fluidops.fedx.evaluation.iterator;

import org.eclipse.rdf4j.common.iteration.ExceptionConvertingIteration;
import org.eclipse.rdf4j.common.iteration.Iteration;
import org.eclipse.rdf4j.query.QueryEvaluationException;
import org.eclipse.rdf4j.repository.RepositoryResult;


/**
 * Convenience iteration to convert {@link RepositoryResult} exceptions to {@link QueryEvaluationException}.
 *  
 * @author Andreas Schwarte
 *
 * @param <T>
 */
public class RepositoryExceptionConvertingIteration<T> extends ExceptionConvertingIteration<T, QueryEvaluationException> {

	public RepositoryExceptionConvertingIteration(
			Iteration<? extends T, ? extends Exception> iter) {
		super(iter);
	}

	@Override
	protected QueryEvaluationException convert(Exception e) {
		return new QueryEvaluationException(e);
	}
}
