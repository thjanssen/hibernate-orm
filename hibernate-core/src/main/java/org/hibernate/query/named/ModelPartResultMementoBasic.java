/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.named;

import org.hibernate.metamodel.mapping.BasicValuedModelPart;

/**
 * @author Steve Ebersole
 */
public interface ModelPartResultMementoBasic extends ModelPartResultMemento, ResultMementoBasic {
	@Override
	BasicValuedModelPart getReferencedModelPart();
}