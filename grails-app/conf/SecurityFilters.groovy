/*
    Copyright 2015 Australian National Botanic Gardens

    This file is part of NSL services project.

    Licensed under the Apache License, Version 2.0 (the "License"); you may not
    use this file except in compliance with the License. You may obtain a copy
    of the License at http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

/**
 * Generated by the Shiro plugin. This filters class protects all URLs
 * via access control by convention.
 */

import au.org.biodiversity.nsl.ApiKeyToken
import au.org.biodiversity.nsl.JsonWebToken
import au.org.biodiversity.nsl.api.ResultObject
import grails.converters.JSON
import io.jsonwebtoken.ExpiredJwtException
import org.apache.shiro.SecurityUtils
import org.apache.shiro.authc.AuthenticationException
import org.apache.shiro.subject.SimplePrincipalCollection
import org.codehaus.groovy.grails.web.util.WebUtils

import javax.crypto.spec.SecretKeySpec
import java.security.Key

import static org.springframework.http.HttpStatus.*

class SecurityFilters {

    def adminService
    def configService

    def filters = {

        all(uri: "/**") {
            before = {
                //fix bug in Grails <2.4.5
                response.setCharacterEncoding('UTF-8')
                //need the .format to get a good response in case of errors
                String requested = (WebUtils.getForwardURI(request) ?: request.getAttribute('javax.servlet.error.request_uri'))
                requested = requested.decodeURL()

                if (requested.endsWith('.json')) {
                    params.format = 'json'
                }
                if (requested.endsWith('.xml')) {
                    params.format = 'xml'
                }
                if (requested.endsWith('.html')) {
                    params.format = 'html'
                }

                //if an apiKey is set then login with it
                if (params.apiKey) {
                    try {
                        String apiKey = params.remove('apiKey')
                        ApiKeyToken authToken = new ApiKeyToken(apiKey, null as char[], SecurityUtils.subject.host as String)
                        Long start = System.currentTimeMillis()
                        SecurityUtils.subject.login(authToken)

                        // TODO: make a new permission "mayRunAsAnyUser"
                        String runAs = params.remove('as')
                        if (runAs) {
                            log.debug("${SecurityUtils.subject.principal} is running as ${runAs}")
                            SecurityUtils.subject.runAs(new SimplePrincipalCollection(runAs, ""))
                        }

                        log.debug "login took ${System.currentTimeMillis() - start}ms"
                        return true
                    } catch (AuthenticationException e) {
                        log.info e.message
                        redirect(controller: 'auth', action: 'unauthorized', params: [format: params.format])
                        return false
                    }
                }

                // if a JSON token is set then log in with that
                if (request.getHeader('Authorization') && request.getHeader('Authorization').startsWith("JWT ")) {
                    try {
                        String jwt = request.getHeader('Authorization').substring(4)
                        Key key = new SecretKeySpec(configService.JWTSecret.getBytes('UTF-8'), 'plain text')
                        JsonWebToken jwToken = new JsonWebToken(jwt, key) //this may throw an Authentication exception
                        println jwToken.claims.toString()
                        SecurityUtils.subject.login(jwToken)
                        return true
                    } catch (ExpiredJwtException expiredJwtException) {
                        log.info "Expired JWT $expiredJwtException.message"
                        render(status: UNAUTHORIZED)
                    } catch (e) {
                        log.info e.message
                    }
                    return false
                }
            }
        }

        notApi(uri: "/**") {
            before = {
                if (adminService.serviceMode()) {
                    accessControl(auth: true) {
                        return true
                    }
                }
            }
        }
    }
}
