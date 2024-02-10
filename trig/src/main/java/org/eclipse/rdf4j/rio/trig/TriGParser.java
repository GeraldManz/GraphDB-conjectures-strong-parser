/*******************************************************************************
 * Copyright (c) 2015 Eclipse RDF4J contributors, Aduna, and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 *******************************************************************************/
package org.eclipse.rdf4j.rio.trig;

import java.io.IOException;
import java.sql.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import org.eclipse.rdf4j.common.text.ASCIIUtil;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.XSD;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFHandlerException;
import org.eclipse.rdf4j.rio.RDFParseException;
import org.eclipse.rdf4j.rio.helpers.BasicParserSettings;
import org.eclipse.rdf4j.rio.turtle.TurtleParser;
import org.eclipse.rdf4j.rio.turtle.TurtleUtil;

/**
 * RDF parser for <a href="https://www.w3.org/TR/trig/">RDF-1.1 TriG</a> files. This parser is not thread-safe,
 * therefore its public methods are synchronized.
 *
 * @author Arjohn Kampman
 * @author Peter Ansell
 * @see TurtleParser
 */
public class TriGParser extends TurtleParser {

	/*-----------*
	 * Variables *
	 *-----------*/

	private Resource context;

	private Boolean isParseConj;

	private Boolean isParseSett;

	private ArrayList<String> conjContainer;

	private ArrayList<String> settContainer;

	private Resource copiedContext;

	private Resource contextOrSubjectSett;

	/*--------------*
	 * Constructors *
	 *--------------*/

	/**
	 * Creates a new TriGParser that will use a {@link SimpleValueFactory} to create RDF model objects.
	 */
	public TriGParser() {
		super();
		isParseConj = false;
		isParseSett = false;
		conjContainer = new ArrayList<>();
		settContainer = new ArrayList<>();
		copiedContext = null;
	}

	/**
	 * Creates a new TriGParser that will use the supplied ValueFactory to create RDF model objects.
	 *
	 * @param valueFactory A ValueFactory.
	 */
	public TriGParser(ValueFactory valueFactory) {
		super(valueFactory);
		isParseConj = false;
		isParseSett = false;
		conjContainer = new ArrayList<>();
		settContainer = new ArrayList<>();
		copiedContext = null;
	}

	/*---------*
	 * Methods *
	 *---------*/

	@Override
	public RDFFormat getRDFFormat() {
		return RDFFormat.TRIG;
	}

	@Override
	protected void parseStatement() throws IOException, RDFParseException, RDFHandlerException {
		StringBuilder sb = new StringBuilder(8);
		setIsParseConj(false);
		setIsParseSett(false);

		int c;
		// longest valid directive @prefix
		do {
			c = readCodePoint();
			if (c == -1 || TurtleUtil.isWhitespace(c)) {
				unread(c);
				break;
			}
			sb.append((char) c);
		} while (sb.length() < 8);

		String directive = sb.toString();

		if (directive.startsWith("@")) {
			parseDirective(directive);
			skipWSC();
			verifyCharacterOrFail(readCodePoint(), ".");
		} else if ((directive.length() >= 6 && directive.substring(0, 6).equalsIgnoreCase("prefix"))
				|| (directive.length() >= 4 && directive.substring(0, 4).equalsIgnoreCase("base"))) {
			parseDirective(directive);
			skipWSC();
			// SPARQL BASE and PREFIX lines do not end in .
		} else if (directive.length() >= 6 && directive.substring(0, 5).equalsIgnoreCase("GRAPH")
				&& directive.substring(5, 6).equals(":")) {
			// If there was a colon immediately after the graph keyword then
			// assume it was a pname and not the SPARQL GRAPH keyword
			unread(directive);
			parseGraph();
		} else if (directive.length() >= 5 && directive.substring(0, 4).equalsIgnoreCase("CONJ")
				&& directive.substring(4, 5).equals(":")) {
			// If there was a colon immediately after the graph keyword then
			// assume it was a pname and not the SPARQL GRAPH keyword
			unread(directive);
			parseConj();
		} else if (directive.length() >= 5 && directive.substring(0, 4).equalsIgnoreCase("SETT")
				&& directive.substring(4, 5).equals(":")) {
			// If there was a colon immediately after the graph keyword then
			// assume it was a pname and not the SPARQL GRAPH keyword
			unread(directive);
			parseSett();
		} else if (directive.length() >= 5 && directive.substring(0, 5).equalsIgnoreCase("GRAPH")) {
			// Do not unread the directive if it was SPARQL GRAPH
			// Just continue with TriG parsing at this point
			skipWSC();

			parseGraph();
			if (getContext() == null) {
				reportFatalError("Missing GRAPH label or subject");
			}
		} else if (directive.length() >= 4 && directive.substring(0, 4).equalsIgnoreCase("CONJ")) {
			// Do not unread the directive if it was SPARQL GRAPH
			// Just continue with TriG parsing at this point
			skipWSC();

			parseConj();
			if (getContext() == null) {
				reportFatalError("Missing GRAPH label or subject");
			}
		} else if (directive.length() >= 4 && directive.substring(0, 4).equalsIgnoreCase("SETT")) {
			// Do not unread the directive if it was SPARQL GRAPH
			// Just continue with TriG parsing at this point
			skipWSC();

			parseSett();
			if (getContext() == null) {
				reportFatalError("Missing GRAPH label or subject");
			}
		} else {
			unread(directive);
			parseGraph();
		}
	}

	protected void parseGraph() throws IOException, RDFParseException, RDFHandlerException {
		int c = readCodePoint();
		int c2 = peekCodePoint();
		Resource contextOrSubject = null;
		boolean foundContextOrSubject = false;
		if (c == '[') {
			skipWSC();
			c2 = readCodePoint();
			if (c2 == ']') {
				contextOrSubject = createNode();
				foundContextOrSubject = true;
				skipWSC();
			} else {
				unread(c2);
				unread(c);
			}
			c = readCodePoint();
		} else if (c == '<' || TurtleUtil.isPrefixStartChar(c) || (c == ':' && c2 != '-') || (c == '_' && c2 == ':')) {
			unread(c);

			Value value = parseValue();

			if (value instanceof Resource) {
				contextOrSubject = (Resource) value;
				foundContextOrSubject = true;
			} else {
				// NOTE: If a user parses Turtle using TriG, then the following
				// could actually be "Illegal subject name", but it should still
				// hold
				reportFatalError("Illegal graph name: " + value);
			}

			skipWSC();
			c = readCodePoint();
		} else {
			setContext(null);
		}

		if (c == '{') {
			setContext(contextOrSubject);

			c = skipWSC();

			if (c != '}') {
				parseTriples();

				c = skipWSC();

				while (c == '.') {
					readCodePoint();

					c = skipWSC();

					if (c == '}') {
						break;
					}

					parseTriples();

					c = skipWSC();
				}

				verifyCharacterOrFail(c, "}");
			}
		} else {
			setContext(null);

			// Did not turn out to be a graph, so assign it to subject instead
			// and
			// parse from here to triples
			if (foundContextOrSubject) {
				subject = contextOrSubject;
				unread(c);
				parsePredicateObjectList();
			}
			// Or if we didn't recognise anything, just parse as Turtle
			else {
				unread(c);
				parseTriples();
			}
		}

		readCodePoint();
	}

	protected void parseConj() throws IOException, RDFParseException, RDFHandlerException {
		int c = readCodePoint();
		int c2 = peekCodePoint();
		Resource contextOrSubject = null;
		boolean foundContextOrSubject = false;
		if (c == '[') {
			skipWSC();
			c2 = readCodePoint();
			if (c2 == ']') {
				contextOrSubject = createNode();
				foundContextOrSubject = true;
				skipWSC();
			} else {
				unread(c2);
				unread(c);
			}
			c = readCodePoint();
		} else if (c == '<' || TurtleUtil.isPrefixStartChar(c) || (c == ':' && c2 != '-') || (c == '_' && c2 == ':')) {
			unread(c);

			setIsParseConj(true);

			Value value = parseValue();
			System.out.println("value = " + value);

			if (value instanceof Resource) {
				contextOrSubject = (Resource) value;
				foundContextOrSubject = true;
			} else {
				// NOTE: If a user parses Turtle using TriG, then the following
				// could actually be "Illegal subject name", but it should still
				// hold
				reportFatalError("Illegal graph name: " + value);
			}

			skipWSC();
			c = readCodePoint();
		} else {
			setContext(null);
		}

		if (c == '{') {
			setContext(contextOrSubject);

			c = skipWSC();

			if (c != '}') {
				parseTriples();

				c = skipWSC();

				while (c == '.') {
					readCodePoint();

					c = skipWSC();

					if (c == '}') {
						break;
					}

					parseTriples();

					c = skipWSC();
				}

				verifyCharacterOrFail(c, "}");
			}
		} else {
			setContext(null);

			// Did not turn out to be a graph, so assign it to subject instead
			// and
			// parse from here to triples
			if (foundContextOrSubject) {
				subject = contextOrSubject;
				unread(c);
				parsePredicateObjectList();
			}
			// Or if we didn't recognise anything, just parse as Turtle
			else {
				unread(c);
				parseTriples();
			}
		}

		readCodePoint();
	}

	protected void parseSett() throws IOException, RDFParseException, RDFHandlerException {
		int c = readCodePoint();
		int c2 = peekCodePoint();
		contextOrSubjectSett = null;
		boolean foundContextOrSubject = false;
		if (c == '[') {
			skipWSC();
			c2 = readCodePoint();
			if (c2 == ']') {
				contextOrSubjectSett = createNode();
				foundContextOrSubject = true;
				skipWSC();
			} else {
				unread(c2);
				unread(c);
			}
			c = readCodePoint();
		} else if (c == '<' || TurtleUtil.isPrefixStartChar(c) || (c == ':' && c2 != '-') || (c == '_' && c2 == ':')) {
			unread(c);

			setIsParseConj(true);
			setIsParseSett(true);

			Value value = parseValue();
			System.out.println("value = " + value);

			if (value instanceof Resource) {
				contextOrSubjectSett = (Resource) value;
				foundContextOrSubject = true;
			} else {
				// NOTE: If a user parses Turtle using TriG, then the following
				// could actually be "Illegal subject name", but it should still
				// hold
				reportFatalError("Illegal graph name: " + value);
			}

			skipWSC();
			c = readCodePoint();
		} else {
			setContext(null);
		}

		if (c == '{') {
			setContext(contextOrSubjectSett);

			c = skipWSC();

			if (c != '}') {
				parseTriples();

				c = skipWSC();

				while (c == '.') {
					readCodePoint();

					c = skipWSC();

					if (c == '}') {
						break;
					}

					parseTriples();

					c = skipWSC();
				}

				verifyCharacterOrFail(c, "}");
			}

			// aggiungo la tripla che determina se una congettura è collassata
			setContext(copiedContext);
			reportStatement(getContext(), createURI(getNamespace("conj") + "settles"), (Value) contextOrSubjectSett);
		} else {
			setContext(null);

			// Did not turn out to be a graph, so assign it to subject instead
			// and
			// parse from here to triples
			if (foundContextOrSubject) {
				subject = contextOrSubjectSett;
				unread(c);
				parsePredicateObjectList();
			}
			// Or if we didn't recognise anything, just parse as Turtle
			else {
				unread(c);
				parseTriples();
			}
		}

		readCodePoint();
	}

	@Override
	protected void parseTriples() throws IOException, RDFParseException, RDFHandlerException {
		setIsParseConj(false);
		int c = peekCodePoint();

		// If the first character is an open bracket we need to decide which of
		// the two parsing methods for blank nodes to use
		if (c == '[') {
			c = readCodePoint();
			skipWSC();
			c = peekCodePoint();
			if (c == ']') {
				c = readCodePoint();
				subject = createNode();
				skipWSC();
				parsePredicateObjectList();
			} else {
				unread('[');
				subject = parseImplicitBlank();
			}
			skipWSC();
			c = peekCodePoint();

			// if this is not the end of the statement, recurse into the list of
			// predicate and objects, using the subject parsed above as the
			// subject
			// of the statement.
			if (c != '.' && c != '}') {
				parsePredicateObjectList();
			}
		} else {
			parseSubject();
			skipWSC();
			parsePredicateObjectList();

			if (getIsParseSett()) {
				setContext(copiedContext);
				reportStatement(subject, predicate, object);
				setContext(contextOrSubjectSett);
			}
		}

		subject = null;
		predicate = null;
		object = null;
	}

	@Override
	protected void reportStatement(Resource subj, IRI pred, Value obj) throws RDFParseException, RDFHandlerException {
		Statement st = createStatement(subj, pred, obj, getContext());
		if (rdfHandler != null) {
			rdfHandler.handleStatement(st);
		}
	}

	@Override
	protected IRI parseURI() throws IOException, RDFParseException {
		StringBuilder uriBuf;
		if (getIsParseConj())
			uriBuf = new StringBuilder("conj-");
		// else if (getIsParseSett())
		// uriBuf = new StringBuilder("sett-");
		else
			uriBuf = new StringBuilder();
		StringBuilder trueUriBuf = new StringBuilder();
		// First character should be '<'
		int c = readCodePoint();
		verifyCharacterOrFail(c, "<");

		boolean uriIsIllegal = false;
		// Read up to the next '>' character
		while (true) {
			c = readCodePoint();

			if (c == '>') {
				break;
			} else if (c == -1) {
				throwEOFException();
			}

			if (c == ' ') {
				reportError("IRI included an unencoded space: '" + c + "'", BasicParserSettings.VERIFY_URI_SYNTAX);
				uriIsIllegal = true;
			}

			appendCodepoint(uriBuf, c);
			if (getIsParseConj())
				appendCodepoint(trueUriBuf, c);

			if (c == '\\') {
				// This escapes the next character, which might be a '>'
				c = readCodePoint();
				if (c == -1) {
					throwEOFException();
				}
				if (c != 'u' && c != 'U') {
					reportError("IRI includes string escapes: '\\" + c + "'", BasicParserSettings.VERIFY_URI_SYNTAX);
					uriIsIllegal = true;
				}
				appendCodepoint(uriBuf, c);
				if (getIsParseConj())
					appendCodepoint(trueUriBuf, c);
			}
		}

		if (getIsParseConj()) {
			if (!conjContainer.contains("<" + trueUriBuf.toString() + ">"))
				conjContainer.add("<" + trueUriBuf.toString() + ">");
			uriBuf = new StringBuilder("conj-" + trueUriBuf.toString());
		} else if (conjContainer.contains("<" + uriBuf.toString() + ">")) {
			uriBuf = new StringBuilder("conj-" + trueUriBuf.toString());
		}
		/*
		 * if (getIsParseSett()) { if (!settContainer.contains("<" + trueUriBuf.toString() + ">")) settContainer.add("<"
		 * + trueUriBuf.toString() + ">"); uriBuf = new StringBuilder("sett-" + trueUriBuf.toString()); } else if
		 * (settContainer.contains("<" + uriBuf.toString() + ">")) { uriBuf = new StringBuilder("sett-" +
		 * trueUriBuf.toString()); }
		 */

		if (c == '.') {
			reportError("IRI must not end in a '.'", BasicParserSettings.VERIFY_URI_SYNTAX);
			uriIsIllegal = true;
		}

		if (getIsParseConj())
			setIsParseConj(false);

		// do not report back the actual URI if it's illegal and the parser is
		// configured to verify URI syntax.
		if (!(uriIsIllegal && getParserConfig().get(BasicParserSettings.VERIFY_URI_SYNTAX))) {
			String uri = uriBuf.toString();
			String trueUri = trueUriBuf.toString();
			// Unescape any escape sequences
			try {
				// FIXME: The following decodes \n and similar in URIs, which
				// should
				// be
				// invalid according to test <turtle-syntax-bad-uri-04.ttl>
				uri = TurtleUtil.decodeString(uri);
				trueUri = TurtleUtil.decodeString(trueUri);
			} catch (IllegalArgumentException e) {
				reportError(e.getMessage(), BasicParserSettings.VERIFY_DATATYPE_VALUES);
			}
			if (getIsParseSett())
				copiedContext = (Resource) super.resolveURI(trueUri);
			return super.resolveURI(uri);
		}

		return null;

	}

	@Override
	protected Value parseQNameOrBoolean() throws IOException, RDFParseException {
		// First character should be a ':' or a letter
		int c = readCodePoint();
		if (c == -1) {
			throwEOFException();
		}
		if (c != ':' && !TurtleUtil.isPrefixStartChar(c)) {
			reportError("Expected a ':' or a letter, found '" + new String(Character.toChars(c)) + "'",
					BasicParserSettings.VERIFY_RELATIVE_URIS);
		}

		String namespace;

		if (c == ':') {
			// qname using default namespace
			namespace = "";
		} else {
			// c is the first letter of the prefix
			StringBuilder prefix = new StringBuilder(8);
			appendCodepoint(prefix, c);

			int previousChar = c;
			c = readCodePoint();
			while (TurtleUtil.isPrefixChar(c)) {
				appendCodepoint(prefix, c);
				previousChar = c;
				c = readCodePoint();
			}
			while (previousChar == '.' && prefix.length() > 0) {
				// '.' is a legal prefix name char, but can not appear at the end
				unread(c);
				c = previousChar;
				prefix.setLength(prefix.length() - 1);
				previousChar = prefix.codePointAt(prefix.codePointCount(0, prefix.length()) - 1);
			}

			if (c != ':') {
				// prefix may actually be a boolean value
				String value = prefix.toString();

				if (value.equals("true")) {
					unread(c);
					return createLiteral("true", null, XSD.BOOLEAN, getLineNumber(), -1);
				} else if (value.equals("false")) {
					unread(c);
					return createLiteral("false", null, XSD.BOOLEAN, getLineNumber(), -1);
				}
			}

			verifyCharacterOrFail(c, ":");

			namespace = prefix.toString();
		}

		// c == ':', read optional local name
		StringBuilder localName = new StringBuilder(16);
		c = readCodePoint();
		if (TurtleUtil.isNameStartChar(c)) {
			if (c == '\\') {
				localName.append(readLocalEscapedChar());
			} else {
				appendCodepoint(localName, c);
			}

			int previousChar = c;
			c = readCodePoint();
			while (TurtleUtil.isNameChar(c)) {
				if (c == '\\') {
					localName.append(readLocalEscapedChar());
				} else {
					appendCodepoint(localName, c);
				}
				previousChar = c;
				c = readCodePoint();
			}

			// Unread last character
			unread(c);

			if (previousChar == '.') {
				// '.' is a legal name char, but can not appear at the end, so
				// is
				// not actually part of the name
				unread(previousChar);
				localName.deleteCharAt(localName.length() - 1);
			}
		} else {
			// Unread last character
			unread(c);
		}

		String localNameString = localName.toString();

		if (namespace == "") { // caso :b
			if (getIsParseConj()) {
				// incontrato dato congetturale (caso: definizione di un nuovo dato congetturale)
				if (!conjContainer.contains(namespace + ":" + localNameString)) {
					// se non presente nell'array di tutte le congetture incontrate allora lo aggiungo
					setNamespace("conj", "conj-" + getNamespace(""));
					conjContainer.add(namespace + ":" + localNameString);
				}
				copiedContext = (Resource) createURI(getNamespace(namespace) + localNameString);
				namespace = "conj";
			} else if (conjContainer.contains(namespace + ":" + localNameString)) {
				// incontrato dato congetturale (caso: soggetto od oggetto di una tripla)
				namespace = "conj";
			}
		} else { // caso a:b
			if (getIsParseConj()) {
				// incontrato dato congetturale (caso: definizione di un nuovo dato congetturale)
				if (!conjContainer.contains(namespace + ":" + localNameString)) {
					// se non presente nell'array di tutte le congetture incontrate allora lo aggiungo
					setNamespace("conj-" + namespace, "conj-" + getNamespace(namespace));
					conjContainer.add(namespace + ":" + localNameString);
				}
				copiedContext = (Resource) createURI(getNamespace(namespace) + localNameString);
				namespace = "conj-" + namespace;
			} else if (conjContainer.contains(namespace + ":" + localNameString)) {
				// incontrato dato congetturale (caso: soggetto od oggetto di una tripla)
				namespace = "conj-" + namespace;
			}
		}

		for (int i = 0; i < localNameString.length(); i++) {
			if (localNameString.charAt(i) == '%') {
				if (i > localNameString.length() - 3 || !ASCIIUtil.isHex(localNameString.charAt(i + 1))
						|| !ASCIIUtil.isHex(localNameString.charAt(i + 2))) {
					reportFatalError("Found incomplete percent-encoded sequence: " + localNameString);
				}
			}
		}

		return createURI(getNamespace(namespace) + localNameString);
	}

	@Override
	protected Resource parseNodeID() throws IOException, RDFParseException {
		// Node ID should start with "_:"
		verifyCharacterOrFail(readCodePoint(), "_");
		verifyCharacterOrFail(readCodePoint(), ":");

		// Read the node ID
		int c = readCodePoint();
		if (c == -1) {
			throwEOFException();
		} else if (!TurtleUtil.isBLANK_NODE_LABEL_StartChar(c)) {
			reportError("Expected a letter, found '" + (char) c + "'", BasicParserSettings.PRESERVE_BNODE_IDS);
		}

		StringBuilder name = new StringBuilder();
		appendCodepoint(name, c);

		// Read all following letter and numbers, they are part of the name
		c = readCodePoint();

		// If we would never go into the loop we must unread now
		if (!TurtleUtil.isBLANK_NODE_LABEL_Char(c)) {
			unread(c);
		}

		while (TurtleUtil.isBLANK_NODE_LABEL_Char(c)) {
			int previous = c;
			c = readCodePoint();

			if (previous == '.' && (c == -1 || TurtleUtil.isWhitespace(c) || c == '<' || c == '_')) {
				unread(c);
				unread(previous);
				break;
			}
			appendCodepoint(name, previous);
			if (!TurtleUtil.isBLANK_NODE_LABEL_Char(c)) {
				unread(c);
			}
		}

		if (getIsParseConj()) {
			setIsParseConj(false);
			if (!conjContainer.contains("_:" + name.toString())) {
				conjContainer.add("_:" + name.toString());
			}
			return createNode("_:" + name.toString());
		} else if (conjContainer.contains("_:" + name.toString())) {
			return createNode("_:" + name.toString());
		}
		return createNode(name.toString());
	}

	private static void appendCodepoint(StringBuilder dst, int codePoint) {
		if (Character.isBmpCodePoint(codePoint)) {
			dst.append((char) codePoint);
		} else if (Character.isValidCodePoint(codePoint)) {
			dst.append(Character.highSurrogate(codePoint));
			dst.append(Character.lowSurrogate(codePoint));
		} else {
			throw new IllegalArgumentException("Invalid codepoint " + codePoint);
		}
	}

	private char readLocalEscapedChar() throws RDFParseException, IOException {
		int c = readCodePoint();

		if (TurtleUtil.isLocalEscapedChar(c)) {
			return (char) c;
		} else {
			throw new RDFParseException("found '" + new String(Character.toChars(c)) + "', expected one of: "
					+ Arrays.toString(TurtleUtil.LOCAL_ESCAPED_CHARS));
		}
	}

	protected void setContext(Resource context) {
		this.context = context;
	}

	protected Resource getContext() {
		return context;
	}

	protected void setIsParseConj(Boolean isParseConj) {
		this.isParseConj = isParseConj;
	}

	protected Boolean getIsParseConj() {
		return isParseConj;
	}

	protected void setIsParseSett(Boolean isParseSett) {
		this.isParseSett = isParseSett;
	}

	protected Boolean getIsParseSett() {
		return isParseSett;
	}
}
