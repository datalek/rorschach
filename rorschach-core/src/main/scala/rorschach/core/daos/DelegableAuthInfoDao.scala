package rorschach.core.daos

import rorschach.core.AuthInfo

import scala.reflect.ClassTag

/**
 * An implementation of the auth info DAO.
 *
 * This abstract implementation of the [[rorschach.core.daos.AuthInfoDao]] trait
 * allows us to get the class tag of the auth info it is responsible for. Based on the class tag
 * the [[rorschach.core.daos.DelegableAuthInfoDao]] class can
 * delegate operations to the DAO which is responsible for the currently handled auth info.
 *
 * @param classTag The class tag for the type parameter.
 * @tparam T The type of the auth info to store.
 */
abstract class DelegableAuthInfoDao[T <: AuthInfo](implicit val classTag: ClassTag[T]) extends AuthInfoDao[T]


