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
package com.fluidops.fedx.evaluation;

import org.eclipse.rdf4j.common.iteration.CloseableIteration;
import org.eclipse.rdf4j.common.iteration.EmptyIteration;
import org.eclipse.rdf4j.common.iteration.ExceptionConvertingIteration;
import org.eclipse.rdf4j.common.iteration.Iterations;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.MalformedQueryException;
import org.eclipse.rdf4j.query.QueryEvaluationException;
import org.eclipse.rdf4j.query.QueryLanguage;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.algebra.StatementPattern;
import org.eclipse.rdf4j.query.algebra.TupleExpr;
import org.eclipse.rdf4j.query.impl.EmptyBindingSet;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.repository.RepositoryResult;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.eclipse.rdf4j.sail.SailConnection;
import org.eclipse.rdf4j.sail.SailException;
import org.eclipse.rdf4j.sail.nativerdf.NativeStoreConnectionExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fluidops.fedx.FederationManager;
import com.fluidops.fedx.algebra.FilterValueExpr;
import com.fluidops.fedx.evaluation.iterator.FilteringInsertBindingsIteration;
import com.fluidops.fedx.evaluation.iterator.FilteringIteration;
import com.fluidops.fedx.evaluation.iterator.InsertBindingsIteration;
import com.fluidops.fedx.evaluation.iterator.StatementConversionIteration;
import com.fluidops.fedx.structures.Endpoint;
import com.fluidops.fedx.util.QueryAlgebraUtil;


/**
 * A triple source to be used on any repository.
 * 
 * @author Andreas Schwarte
 *
 */
public class SailTripleSource extends TripleSourceBase implements TripleSource{

	public static Logger log = LoggerFactory.getLogger(SailTripleSource.class);
	

	SailTripleSource(Endpoint endpoint) {
		super(FederationManager.getMonitoringService(), endpoint);
	}
	
	@Override
	public CloseableIteration<BindingSet, QueryEvaluationException> getStatements(
			String preparedQuery, RepositoryConnection conn, final BindingSet bindings, final FilterValueExpr filterExpr)
			throws RepositoryException, MalformedQueryException,
			QueryEvaluationException {
		
		TupleQuery query = conn.prepareTupleQuery(QueryLanguage.SPARQL, preparedQuery, null);
		disableInference(query);		
		
		// evaluate the query
		CloseableIteration<BindingSet, QueryEvaluationException> res = query.evaluate();
		
		// apply filter and/or insert original bindings
		if (filterExpr!=null) {
			if (bindings.size()>0) 
				res = new FilteringInsertBindingsIteration(filterExpr, bindings, res);
			else
				res = new FilteringIteration(filterExpr, res);
			if (!res.hasNext())
				return new EmptyIteration<BindingSet, QueryEvaluationException>();
		} else if (bindings.size()>0) {
			res = new InsertBindingsIteration(res, bindings);
		}
		
		return res;
	}

	@Override
	public CloseableIteration<BindingSet, QueryEvaluationException> getStatements(
			StatementPattern stmt, RepositoryConnection conn,
			final BindingSet bindings, FilterValueExpr filterExpr)
			throws RepositoryException, MalformedQueryException,
			QueryEvaluationException  {
	
		Value subjValue = QueryAlgebraUtil.getVarValue(stmt.getSubjectVar(), bindings);
		Value predValue = QueryAlgebraUtil.getVarValue(stmt.getPredicateVar(), bindings);
		Value objValue = QueryAlgebraUtil.getVarValue(stmt.getObjectVar(), bindings);			
		
		RepositoryResult<Statement> repoResult = conn.getStatements((Resource) subjValue, (IRI) predValue, objValue,
				true, new Resource[0]);
				
		// XXX implementation remark and TODO taken from Sesame
		// The same variable might have been used multiple times in this
		// StatementPattern, verify value equality in those cases.
		
		// an iterator that converts the statements to var bindings
		CloseableIteration<BindingSet, QueryEvaluationException> res = new StatementConversionIteration(repoResult, bindings, stmt);
				
		// if filter is set, apply it
		if (filterExpr!=null) {
			CloseableIteration<BindingSet, QueryEvaluationException> filteredRes = new FilteringIteration(filterExpr,
					res);
			if (!filteredRes.hasNext())
			{
				Iterations.closeCloseable(filteredRes);
				return new EmptyIteration<BindingSet, QueryEvaluationException>();
			}
			return filteredRes;
		}
		else
		{
			return res;
		}
		
	}

	@Override
	public CloseableIteration<Statement, QueryEvaluationException> getStatements(RepositoryConnection conn,
			Resource subj, IRI pred, Value obj, Resource... contexts)
			throws RepositoryException,
			MalformedQueryException, QueryEvaluationException {
		
		// TODO add handling for contexts
		RepositoryResult<Statement> repoResult = conn.getStatements(subj, pred, obj, true);
		
		// XXX implementation remark and TODO taken from Sesame
		// The same variable might have been used multiple times in this
		// StatementPattern, verify value equality in those cases.
		
		return new ExceptionConvertingIteration<Statement, QueryEvaluationException>(repoResult) {
			@Override
			protected QueryEvaluationException convert(Exception arg0) {
				return new QueryEvaluationException(arg0);
			}
		};
	}
	
	
	@Override
	public boolean hasStatements(StatementPattern stmt,
			RepositoryConnection conn, BindingSet bindings)
			throws RepositoryException, MalformedQueryException,
			QueryEvaluationException {

		Value subjValue = QueryAlgebraUtil.getVarValue(stmt.getSubjectVar(), bindings);
		Value predValue = QueryAlgebraUtil.getVarValue(stmt.getPredicateVar(), bindings);
		Value objValue = QueryAlgebraUtil.getVarValue(stmt.getObjectVar(), bindings);
		
		return conn.hasStatement((Resource) subjValue, (IRI) predValue, objValue, true, new Resource[0]);
	}

	@Override
	public boolean usePreparedQuery() {
		return false;
	}

	@SuppressWarnings("unchecked")
	@Override
	public CloseableIteration<BindingSet, QueryEvaluationException> getStatements(
			TupleExpr preparedQuery, RepositoryConnection conn,
			BindingSet bindings, FilterValueExpr filterExpr)
			throws RepositoryException, MalformedQueryException,
			QueryEvaluationException {
		
		/*
		 * Implementation note:
		 * 
		 *  a hook is introduced for NativeStore instances such that an extended
		 *  connection is used. The extended connection provides a method to
		 *  evaluate prepared queries without prior (obsolete) optimization.  
		 */
	
		SailConnection sailConn = ((SailRepositoryConnection)conn).getSailConnection();
		
		CloseableIteration<BindingSet, QueryEvaluationException> res;
		if (sailConn instanceof NativeStoreConnectionExt) {
			NativeStoreConnectionExt _conn = (NativeStoreConnectionExt)sailConn;
			res = (CloseableIteration<BindingSet, QueryEvaluationException>) _conn.evaluatePrecompiled(preparedQuery);
		} else {
			try {
				log.warn("Precompiled query optimization for native store could not be applied: use extended NativeStore initialization using NativeStoreConnectionExt");
				res = (CloseableIteration<BindingSet, QueryEvaluationException>) sailConn.evaluate(preparedQuery, null, EmptyBindingSet.getInstance(), true);
			} catch (SailException e) {
				throw new QueryEvaluationException(e);
			}
		}
		
		if (bindings.size()>0) {
			res = new InsertBindingsIteration(res, bindings);
		}
		
		return res;
	}

	
}
