/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.metamodel.internal;

import java.util.Map;

import org.hibernate.bytecode.spi.ReflectionOptimizer;
import org.hibernate.mapping.Component;
import org.hibernate.mapping.Property;
import org.hibernate.metamodel.RepresentationMode;
import org.hibernate.metamodel.spi.EmbeddableInstantiator;
import org.hibernate.metamodel.spi.EmbeddableRepresentationStrategy;
import org.hibernate.metamodel.spi.RuntimeModelCreationContext;
import org.hibernate.property.access.internal.PropertyAccessStrategyMapImpl;
import org.hibernate.property.access.spi.PropertyAccess;
import org.hibernate.type.descriptor.java.JavaTypeDescriptor;

/**
 * @author Steve Ebersole
 */
public class EmbeddableRepresentationStrategyMap implements EmbeddableRepresentationStrategy {
	private final JavaTypeDescriptor<?> mapJtd;
	private final EmbeddableInstantiator instantiator;

	public EmbeddableRepresentationStrategyMap(
			Component bootDescriptor,
			RuntimeModelCreationContext creationContext) {
		this.mapJtd = creationContext.getTypeConfiguration().getJavaTypeDescriptorRegistry().getDescriptor( Map.class );
		this.instantiator = new EmbeddableInstantiatorDynamicMap( bootDescriptor );
	}

	@Override
	public RepresentationMode getMode() {
		return RepresentationMode.MAP;
	}

	@Override
	public ReflectionOptimizer getReflectionOptimizer() {
		return null;
	}

	@Override
	public JavaTypeDescriptor<?> getMappedJavaTypeDescriptor() {
		return mapJtd;
	}

	@Override
	public PropertyAccess resolvePropertyAccess(Property bootAttributeDescriptor) {
		return PropertyAccessStrategyMapImpl.INSTANCE.buildPropertyAccess(
				null,
				bootAttributeDescriptor.getName()
		);
	}

	@Override
	public EmbeddableInstantiator getInstantiator() {
		return instantiator;
	}
}