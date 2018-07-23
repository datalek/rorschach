package rorschach.providers.oauth

import rorschach.core.Provider

trait OAuth2Provider[R, P, O] extends Provider[R, Either[O, P]]
