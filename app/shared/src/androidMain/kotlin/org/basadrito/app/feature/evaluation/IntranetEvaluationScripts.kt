package org.basadrito.app.feature.evaluation

import org.json.JSONObject

internal fun evaluationFillScript(rating: Int, comment: String): String {
    require(rating in 1..5)
    require(comment.isNotBlank() && comment.length <= 500)
    val commentLiteral = JSONObject.quote(comment).replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")
    return """
        (() => {
          if (location.origin !== 'https://intranet.unjbg.edu.pe' ||
              !/^\/alumno\/evaluacion-docente\/[^/]+\/encuesta\/[^/]+\/seccion\/?${'$'}/.test(location.pathname)) return 0;
          const form = Array.from(document.forms).find(f => {
            const action = new URL(f.action, location.href);
            return action.origin === location.origin && action.pathname === '/alumno/evaluacion-docente/encuesta/enviar';
          });
          if (!form) return 0;
          const radios = Array.from(form.querySelectorAll('input[type="radio"]'))
              .filter(e => /^Questions\[\d+\]\.Value${'$'}/.test(e.name));
          const groups = Array.from(new Set(radios.map(e => e.name)));
          // Si cambia la escala, no interpreta el formulario ni sobrescribe respuestas.
          if (!groups.length || groups.some(name => {
              const values = radios.filter(e => e.name === name).map(e => e.value).sort().join(',');
              return values !== '1,2,3,4,5';
          })) return -1;
          let filled = 0;
          for (const name of groups) {
            const group = radios.filter(e => e.name === name);
            if (group.some(e => e.checked)) continue;
            const target = group.find(e => e.value === '$rating' && !e.disabled);
            if (target) { target.click(); filled++; }
          }
          const comment = form.querySelector('textarea[name="Commentary"]');
          if (comment && !comment.value.trim()) {
            comment.value = $commentLiteral;
            comment.dispatchEvent(new Event('input', {bubbles:true}));
            comment.dispatchEvent(new Event('change', {bubbles:true}));
          }
          return filled;
        })();
    """.trimIndent()
}

internal fun evaluationAutomationScript(
    rating: Int,
    comment: String,
    allowSubmit: Boolean,
    awaitingSubmission: Boolean,
): String {
    val fillScript = evaluationFillScript(rating, comment).removeSuffix(";")
    return """
        (() => {
          const result = (action, reason = '') => JSON.stringify({action, reason});
          if (location.origin !== 'https://intranet.unjbg.edu.pe') return result('STOP', 'session');
          const visible = e => e && !e.hidden && getComputedStyle(e).display !== 'none' && getComputedStyle(e).visibility !== 'hidden';
          const enabled = e => visible(e) && !e.disabled && e.getAttribute('aria-disabled') !== 'true' && !e.closest('.disabled');
          const text = e => e.textContent.replace(/\s+/g, ' ').trim();
          const state = window.__basadritoEvaluationRun || (window.__basadritoEvaluationRun = {});
          if (Array.from(document.querySelectorAll('.validation-summary-errors,.field-validation-error,.alert-danger'))
              .some(e => visible(e) && text(e))) return result('STOP', 'validation');
          if (location.pathname === '/' || location.pathname === '/perfil') return result('VERIFY');

          if (location.pathname.replace(/\/${'$'}/, '') === '/alumno/evaluacion-docente') {
            const table = document.getElementById('sections-datatable');
            const loading = document.getElementById('sections-datatable_processing');
            if (!table || (loading && visible(loading))) return result('WAIT');
            const body = table.querySelector('tbody');
            if (!body || !body.querySelector('tr')) return result('WAIT');
            const wrapper = document.getElementById('sections-datatable_wrapper');
            const page = wrapper && wrapper.querySelector('.paginate_button.active');
            const signature = (page ? text(page) : '') + '|' + text(body);
            if (state.phase === 'opening') return result('WAIT');
            if (state.phase === 'paging' && signature === state.signature) return result('WAIT');
            const qualify = Array.from(body.querySelectorAll('button,a')).find(e => enabled(e) && text(e) === 'Calificar');
            if (qualify) {
              state.phase = 'opening';
              qualify.click();
              return result('OPEN');
            }
            if (Array.from(body.querySelectorAll('button,a')).some(e => text(e) === 'Calificar')) return result('WAIT');
            const next = wrapper && wrapper.querySelector('.paginate_button.next:not(.disabled) a');
            if (enabled(next)) {
              state.phase = 'paging'; state.signature = signature;
              next.click();
              return result('PAGE');
            }
            return result('VERIFY');
          }

          const survey = /^\/alumno\/evaluacion-docente\/[^/]+\/encuesta\/[^/]+\/seccion\/?${'$'}/.test(location.pathname);
          if (survey) {
            if (state.phase === 'submitted') {
              const continueButton = Array.from(document.querySelectorAll('button,a'))
                .find(e => enabled(e) && text(e) === 'Continuar');
              if (continueButton && !state.continued) {
                state.continued = true; continueButton.click(); return result('CONTINUE');
              }
              return result('WAIT');
            }
            if (!$allowSubmit) return result('STOP', 'duplicate');
            const form = Array.from(document.forms).find(f => {
              const action = new URL(f.action, location.href);
              return action.origin === location.origin && action.pathname === '/alumno/evaluacion-docente/encuesta/enviar' && f.method.toLowerCase() === 'post';
            });
            if (!form) return result('STOP', 'structure');
            if (state.phase !== 'prepared') {
              const filled = $fillScript;
              if (filled < 0) return result('STOP', 'scale');
              const radios = Array.from(form.querySelectorAll('input[type="radio"]')).filter(e => /^Questions\[\d+\]\.Value${'$'}/.test(e.name));
              const groups = Array.from(new Set(radios.map(e => e.name)));
              const comment = form.querySelector('textarea[name="Commentary"]');
              if (!groups.length || groups.some(name => !radios.some(e => e.name === name && e.checked)) ||
                  !comment || !comment.value.trim() || !form.checkValidity()) return result('STOP', 'validation');
              state.phase = 'prepared'; return result('PREPARED');
            }
            if (!form.checkValidity()) return result('STOP', 'validation');
            const submit = Array.from(form.querySelectorAll('button,input[type="submit"]'))
              .find(e => enabled(e) && e.type === 'submit' && (text(e) === 'Enviar' || e.value === 'Enviar'));
            if (!submit) return result('STOP', 'structure');
            state.phase = 'submitted';
            submit.click();
            return result('SUBMITTED');
          }

          // Algunas respuestas del servidor muestran un paso intermedio tras enviar.
          if ($awaitingSubmission && location.pathname.startsWith('/alumno/evaluacion-docente/')) {
            if (state.continued) return result('WAIT');
            const continueButton = Array.from(document.querySelectorAll('button,a'))
              .find(e => enabled(e) && text(e) === 'Continuar');
            if (continueButton && !state.continued) {
              state.continued = true; continueButton.click(); return result('CONTINUE');
            }
          }
          return result('STOP', 'page');
        })();
    """.trimIndent()
}
