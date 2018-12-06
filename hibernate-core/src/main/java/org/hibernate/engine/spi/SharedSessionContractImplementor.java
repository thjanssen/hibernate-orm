/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.engine.spi;

import java.io.Serializable;
import java.sql.Connection;
import java.util.UUID;
import javax.persistence.FlushModeType;

import org.hibernate.CacheMode;
import org.hibernate.FlushMode;
import org.hibernate.HibernateException;
import org.hibernate.Interceptor;
import org.hibernate.SharedSessionContract;
import org.hibernate.Transaction;
import org.hibernate.cache.spi.CacheTransactionSynchronization;
import org.hibernate.cfg.Environment;
import org.hibernate.collection.spi.PersistentCollection;
import org.hibernate.engine.jdbc.LobCreationContext;
import org.hibernate.engine.jdbc.spi.JdbcCoordinator;
import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.internal.util.config.ConfigurationHelper;
import org.hibernate.metamodel.model.domain.spi.EntityTypeDescriptor;
import org.hibernate.metamodel.model.domain.spi.PersistentCollectionDescriptor;
import org.hibernate.query.spi.QueryParameterBindingTypeResolver;
import org.hibernate.query.spi.QueryProducerImplementor;
import org.hibernate.query.sqm.consume.multitable.spi.HandlerCreationContext;
import org.hibernate.resource.jdbc.spi.JdbcSessionOwner;
import org.hibernate.resource.transaction.spi.TransactionCoordinator;
import org.hibernate.resource.transaction.spi.TransactionCoordinatorBuilder.Options;
import org.hibernate.type.Type;
import org.hibernate.type.descriptor.spi.WrapperOptions;

/**
 * Defines the internal contract shared between {@link org.hibernate.Session} and
 * {@link org.hibernate.StatelessSession} as used by other parts of Hibernate (such as
 * {@link Type}, {@link EntityTypeDescriptor} and
 * {@link PersistentCollectionDescriptor} implementors
 *
 * A Session, through this interface and SharedSessionContractImplementor, implements:<ul>
 *     <li>
 *         {@link org.hibernate.resource.jdbc.spi.JdbcSessionOwner} to drive the behavior of a "JDBC session".
 *         Can therefor be used to construct a JdbcCoordinator, which (for now) models a "JDBC session"
 *     </li>
 *     <li>
 *         {@link Options}
 *         to drive the creation of the {@link TransactionCoordinator} delegate.
 *         This allows it to be passed along to
 *         {@link org.hibernate.resource.transaction.spi.TransactionCoordinatorBuilder#buildTransactionCoordinator}
 *     </li>
 *     <li>
 *         {@link org.hibernate.engine.jdbc.LobCreationContext} to act as the context for JDBC LOB instance creation
 *     </li>
 *     <li>
 *         {@link WrapperOptions} to fulfill the behavior needed while
 *         binding/extracting values to/from JDBC as part of the Type contracts
 *     </li>
 * </ul>
 *
 * @author Gavin King
 * @author Steve Ebersole
 */
public interface SharedSessionContractImplementor
		extends SharedSessionContract, JdbcSessionOwner, Options, LobCreationContext, WrapperOptions, QueryProducerImplementor,
		HandlerCreationContext, QueryParameterBindingTypeResolver {

	// todo : this is the shared contract between Session and StatelessSession, but it defines methods that StatelessSession does not implement
	//	(it just throws UnsupportedOperationException).  To me it seems like it is better to properly isolate those methods
	//	into just the Session hierarchy.  They include (at least):
	//		1) get/set CacheMode
	//		2) get/set FlushMode
	//		3) get/set (default) read-only
	//		4) #setAutoClear
	//		5) #disableTransactionAutoJoin

	/**
	 * Get the creating <tt>SessionFactoryImplementor</tt>
	 */
	SessionFactoryImplementor getFactory();

	SessionEventListenerManager getEventListenerManager();

	/**
	 * Get the persistence context for this session
	 */
	PersistenceContext getPersistenceContext();

	JdbcCoordinator getJdbcCoordinator();

	JdbcServices getJdbcServices();

	/**
	 * The multi-tenancy tenant identifier, if one.
	 *
	 * @return The tenant identifier; may be {@code null}
	 */
	String getTenantIdentifier();

	/**
	 * A UUID associated with each Session.  Useful mainly for logging.
	 *
	 * @return The UUID
	 */
	UUID getSessionIdentifier();

	/**
	 * Checks whether the session is closed.  Provided separately from
	 * {@link #isOpen()} as this method does not attempt any JTA synchronization
	 * registration, where as {@link #isOpen()} does; which makes this one
	 * nicer to use for most internal purposes.
	 *
	 * @return {@code true} if the session is closed; {@code false} otherwise.
	 */
	boolean isClosed();

	/**
	 * Checks whether the session is open or is waiting for auto-close
	 *
	 * @return {@code true} if the session is closed or if it's waiting for auto-close; {@code false} otherwise.
	 */
	default boolean isOpenOrWaitingForAutoClose() {
		return !isClosed();
	}

	/**
	 * Performs a check whether the Session is open, and if not:<ul>
	 *     <li>marks current transaction (if one) for rollback only</li>
	 *     <li>throws an IllegalStateException (JPA defines the exception type)</li>
	 * </ul>
	 */
	default void checkOpen() {
		checkOpen( true );
	}

	/**
	 * Performs a check whether the Session is open, and if not:<ul>
	 *     <li>if {@code markForRollbackIfClosed} is true, marks current transaction (if one) for rollback only</li>
	 *     <li>throws an IllegalStateException (JPA defines the exception type)</li>
	 * </ul>
	 */
	void checkOpen(boolean markForRollbackIfClosed);

	void prepareForQueryExecution(boolean requiresTxn);

	/**
	 * Marks current transaction (if one) for rollback only
	 */
	void markForRollbackOnly();

	/**
	 * A "timestamp" at or before the start of the current transaction.
	 *
	 * @apiNote This "timestamp" need not be related to timestamp in the Java Date/millisecond
	 * sense.  It just needs to be an incrementing value.  See
	 * {@link CacheTransactionSynchronization#getCurrentTransactionStartTimestamp()}
	 */
	long getTransactionStartTimestamp();

	/**
	 * @deprecated (since 5.3) Use {@link #getTransactionStartTimestamp()} instead.
	 */
	@Deprecated
	default long getTimestamp() {
		return getTransactionStartTimestamp();
	}

	/**
	 * The current CacheTransactionContext associated with the Session.  This may
	 * return {@code null} when the Session is not currently part of a transaction.
	 */
	CacheTransactionSynchronization getCacheTransactionSynchronization();

	/**
	 * Does this <tt>Session</tt> have an active Hibernate transaction
	 * or is there a JTA transaction in progress?
	 */
	boolean isTransactionInProgress();

	/**
	 * Provides access to the underlying transaction or creates a new transaction if
	 * one does not already exist or is active.  This is primarily for internal or
	 * integrator use.
	 *
	 * @return the transaction
     */
	Transaction accessTransaction();

	/**
	 * Hide the changing requirements of entity key creation
	 *
	 * @param id The entity id
	 * @param descriptor The entity descriptor
	 *
	 * @return The entity key
	 */
	EntityKey generateEntityKey(Object id, EntityTypeDescriptor descriptor);

	/**
	 * Retrieves the interceptor currently in use by this event source.
	 *
	 * @return The interceptor.
	 */
	Interceptor getInterceptor();

	/**
	 * Enable/disable automatic cache clearing from after transaction
	 * completion (for EJB3)
	 */
	void setAutoClear(boolean enabled);

	/**
	 * Initialize the collection (if not already initialized)
	 */
	void initializeCollection(PersistentCollection collection, boolean writing)
			throws HibernateException;

	/**
	 * Load an instance without checking if it was deleted.
	 * <p/>
	 * When <tt>nullable</tt> is disabled this method may create a new proxy or
	 * return an existing proxy; if it does not exist, throw an exception.
	 * <p/>
	 * When <tt>nullable</tt> is enabled, the method does not create new proxies
	 * (but might return an existing proxy); if it does not exist, return
	 * <tt>null</tt>.
	 * <p/>
	 * When <tt>eager</tt> is enabled, the object is eagerly fetched
	 */
	Object internalLoad(String entityName, Object id, boolean eager, boolean nullable)
			throws HibernateException;

	/**
	 * Load an instance immediately. This method is only called when lazily initializing a proxy.
	 * Do not return the proxy.
	 */
	Object immediateLoad(String entityName, Object id) throws HibernateException;

	/**
	 * Get the <tt>EntityPersister</tt> for any instance
	 *
	 * @param entityName optional entity name
	 * @param object the entity instance
	 *
	 * @deprecated since 6.0, use {@link #getEntityDescriptor(String, Object)} instead.
	 */
	@Deprecated
	default EntityTypeDescriptor getEntityPersister(String entityName, Object object) throws HibernateException {
		return getEntityDescriptor( entityName, object );
	}

	EntityTypeDescriptor getEntityDescriptor(String entityName, Object object) throws HibernateException;

	/**
	 * Get the entity instance associated with the given <tt>Key</tt>,
	 * calling the Interceptor if necessary
	 */
	Object getEntityUsingInterceptor(EntityKey key) throws HibernateException;

	/**
	 * Return the identifier of the persistent object, or null if
	 * not associated with the session
	 */
	Object getContextEntityIdentifier(Object object);

	/**
	 * The best guess entity name for an entity not in an association
	 */
	String bestGuessEntityName(Object object);

	/**
	 * The guessed entity name for an entity not in an association
	 */
	String guessEntityName(Object entity) throws HibernateException;

	/**
	 * Instantiate the entity class, initializing with the given identifier
	 */
	Object instantiate(String entityName, Object id) throws HibernateException;

	/**
	 * @deprecated Use {@link #instantiate(String, Object)} instead
	 */
	@Deprecated
	default Object instantiate(String entityName, Serializable id) throws HibernateException {
		return instantiate( entityName, (Object) id );
	}

	boolean isDefaultReadOnly();

	int getDontFlushFromFind();

	CacheMode getCacheMode();

	void setCacheMode(CacheMode cm);

	/**
	 * Set the flush mode for this session.
	 * <p/>
	 * The flush mode determines the points at which the session is flushed.
	 * <i>Flushing</i> is the process of synchronizing the underlying persistent
	 * store with persistable state held in memory.
	 * <p/>
	 * For a logically "read only" session, it is reasonable to set the session's
	 * flush mode to {@link FlushMode#MANUAL} at the start of the session (in
	 * order to achieve some extra performance).
	 *
	 * @param flushMode the new flush mode
	 *
	 * @deprecated (since 5.2) use {@link #setHibernateFlushMode(FlushMode)} instead
	 */
	@Deprecated
	void setFlushMode(FlushMode flushMode);

	/**
	 * Get the flush mode for this session.
	 * <p/>
	 * For users of the Hibernate native APIs, we've had to rename this method
	 * as defined by Hibernate historically because the JPA contract defines a method of the same
	 * name, but returning the JPA {@link FlushModeType} rather than Hibernate's {@link FlushMode}.  For
	 * the former behavior, use {@link #getHibernateFlushMode()} instead.
	 *
	 * @return The FlushModeType in effect for this Session.
	 */
	FlushModeType getFlushMode();

	/**
	 * Set the flush mode for this session.
	 * <p/>
	 * The flush mode determines the points at which the session is flushed.
	 * <i>Flushing</i> is the process of synchronizing the underlying persistent
	 * store with persistable state held in memory.
	 * <p/>
	 * For a logically "read only" session, it is reasonable to set the session's
	 * flush mode to {@link FlushMode#MANUAL} at the start of the session (in
	 * order to achieve some extra performance).
	 *
	 * @param flushMode the new flush mode
	 */
	void setHibernateFlushMode(FlushMode flushMode);

	/**
	 * Get the current flush mode for this session.
	 *
	 * @return The flush mode
	 */
	FlushMode getHibernateFlushMode();

	Connection connection();

	void flush();

	boolean isEventSource();

	void afterScrollOperation();

	boolean shouldAutoClose();

	boolean isAutoCloseSessionEnabled();

	default boolean isQueryParametersValidationEnabled(){
		return getFactory().getSessionFactoryOptions().isQueryParametersValidationEnabled();
	}

	/**
	 * Get the load query influencers associated with this session.
	 *
	 * @return the load query influencers associated with this session;
	 *         should never be null.
	 */
	LoadQueryInfluencers getLoadQueryInfluencers();

	ExceptionConverter getExceptionConverter();

	/**
	 * Get the currently configured JDBC batch size either at the Session-level or SessionFactory-level.
	 *
	 * If the Session-level JDBC batch size was not configured, return the SessionFactory-level one.
	 *
	 * @return Session-level or or SessionFactory-level JDBC batch size.
	 *
	 * @since 5.2
	 *
	 * @see org.hibernate.boot.spi.SessionFactoryOptions#getJdbcBatchSize
	 * @see org.hibernate.boot.SessionFactoryBuilder#applyJdbcBatchSize
	 */
	default Integer getConfiguredJdbcBatchSize() {
		final Integer sessionJdbcBatchSize = getJdbcBatchSize();

		return sessionJdbcBatchSize == null ?
			ConfigurationHelper.getInt(
					Environment.STATEMENT_BATCH_SIZE,
					getFactory().getProperties(),
					1
			) :
			sessionJdbcBatchSize;
	}
}