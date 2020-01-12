const gulp = require('gulp')
const merge = require('merge-stream')
const del = require('del')
const Eleventy = require('@11ty/eleventy')
const ssg = new Eleventy()
const gulpStylelint = require('gulp-stylelint')
const sourcemaps = require('gulp-sourcemaps')
const postcss = require('gulp-postcss')
const path = require('path')
const concat = require('gulp-concat')
const beautify = require('gulp-beautify')
const uglify = require('gulp-uglify')
const rename = require('gulp-rename')
const connect = require('gulp-connect')

/**
  * TODO (paulshryock): Add eleventy to develop pipeline
  */

const paths = {
  emails: {
    layouts: './resources/email/**/*.liquid',
    content: './resources/email/**/*.md',
    dest: './resources/public/email',
    output: './resources/public/email/**/*.html'
  },
  css: {
    all: './resources/css/**/*.css',
    src: './resources/css/style.css',
    dest: './resources/public/css',
    output: './resources/public/css/App.css'
  },
  images: {
    src: './resources/img/**/*',
    dest: './resources/public/img'
  }
}

function clean (cb) {
  del([paths.css.dest])

  return cb()
}

async function email () {
  await ssg.init()
  await ssg.write()

  const options = {
    indent_size: 2,
    max_preserve_newlines: 1
  }

  const emails = gulp.src(paths.emails.output)
    .pipe(beautify.html(options)) // Beautify
    .pipe(gulp.dest(paths.emails.dest))
    .pipe(connect.reload())

  return emails
}

function css () {
  const settings = {
    config: {
      extends: ['stylelint-config-standard'],
      rules: {
        'at-rule-no-unknown': [true, {
          ignoreAtRules: ['include', 'mixin']
        }],
        'no-descending-specificity': null,
        'selector-pseudo-class-no-unknown': null
      }
    },
    fix: true,
    reporters: [
      { formatter: 'string', console: true }
    ]
  }

  const plugins = [
    require('postcss-easy-import'), // @import files
    require('precss'), // Transpile Sass-like syntax
    require('postcss-preset-env'), // Polyfill modern CSS
    require('autoprefixer'), // Add vendor prefixes
    require('pixrem')() // Add fallbacks for rem units
  ]

  const lint = gulp.src(paths.css.all)
    .pipe(gulpStylelint(settings))

  const build = gulp.src(paths.css.src)
    .pipe(sourcemaps.init())
    .pipe(postcss(plugins))
    .pipe(concat('App.css')) // Concatenate and rename
    .pipe(beautify.css({ indent_size: 2 })) // Beautify
    .pipe(sourcemaps.write('.')) // Maintain Sourcemaps
    .pipe(gulp.dest(paths.css.dest))
    .pipe(connect.reload())

  const merged = merge(lint, build)

  return merged.isEmpty() ? null : merged
}

function minify () {
  const css = gulp.src(paths.css.output)
    .pipe(sourcemaps.init())
    .pipe(postcss([require('cssnano')])) // Minify
    .pipe(sourcemaps.write('.')) // Maintain Sourcemaps
    .pipe(gulp.dest(paths.css.dest))
    .pipe(connect.reload())

  return css
}

function images () {
  const images = gulp.src(paths.images.src)
    // TODO (paulshryock): Add image processing
    .pipe(gulp.dest(paths.images.dest))
    .pipe(connect.reload())

  return images
}

function watch (cb) {
  gulp.watch([
    paths.emails.layouts,
    paths.emails.content
  ], email)
  gulp.watch([paths.css.all], css)
  gulp.watch([paths.images.src], images)

  cb()
}

/**
 * Gulp tasks
 */
const develop = gulp.series(
  clean,
  css,
  images,
  email
)

const build = gulp.series(
  develop,
  minify
)

exports.develop = develop
exports.watch = gulp.series(develop, watch)
exports.build = build
exports.default = build
