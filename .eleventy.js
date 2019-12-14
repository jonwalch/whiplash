module.exports = function (eleventyConfig) {

  // Add emails content collection
  eleventyConfig.addCollection('emails', collection => {
    collection
      .getAll()
      .filter(post => post.data.contentType === 'email')
  })

  return {
    dir: {
      data: '_data',
      includes: '_includes',
      input: 'resources/email',
      layouts: '_layouts',
      output: 'resources/public/email'
    }
  }
}
