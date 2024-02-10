/*******************************************************************************
 * Copyright (c) 2020 Eclipse RDF4J contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 *******************************************************************************/
/* Generated By:JJTree: Do not edit this line. ASTTripleRef.java Version 4.3 */
/* JavaCCOptions:MULTI=true,NODE_USES_PARSER=false,VISITOR=true,TRACK_TOKENS=false,NODE_PREFIX=AST,NODE_EXTENDS=,NODE_FACTORY=,SUPPORT_CLASS_VISIBILITY_PUBLIC=true */
package org.eclipse.rdf4j.query.parser.sparql.ast;

public class ASTTripleRef extends SimpleNode {
	public ASTTripleRef(int id) {
		super(id);
	}

	public ASTTripleRef(SyntaxTreeBuilder p, int id) {
		super(p, id);
	}

	public SimpleNode getSubj() {
		return (SimpleNode) jjtGetChild(0);
	}

	public SimpleNode getPred() {
		return (SimpleNode) jjtGetChild(1);
	}

	public SimpleNode getObj() {
		return (SimpleNode) jjtGetChild(2);
	}

	/** Accept the visitor. **/
	@Override
	public Object jjtAccept(SyntaxTreeBuilderVisitor visitor, Object data) throws VisitorException {
		return visitor.visit(this, data);
	}
}
/* JavaCC - OriginalChecksum=08fd6ee0bffb39a414509301100c9e05 (do not edit this line) */
